package org.labkey.htcondorconnector.pipeline;

import com.drew.lang.annotations.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.FileUtil;
import org.labkey.htcondorconnector.HTCondorConnectorSchema;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 10/31/2015.
 */
public class HTCondorExecutionEngine implements RemoteExecutionEngine
{
    private static final Logger _log = Logger.getLogger(HTCondorExecutionEngine.class);
    private HTCondorExecutionEngineConfig _config = new HTCondorExecutionEngineConfig();

    @Override
    public String getType()
    {
        return null;
    }

    @Override
    public void submitJob(PipelineJob job) throws PipelineJobException
    {
        //build submit script
        File submitScript = createSubmitScript(job);

        Map<String, String> ctx = getBaseCtx();
        ctx.put("submitScript", submitScript.getPath());
        String toExec = _config.getSubmitCommandExpr().eval(ctx);
    }

    private Map<String, String> getBaseCtx()
    {
        Map<String, String> map = new HashMap<>();
        map.put("user", _config.getUser());
        
        return map;
    }
    
    private File createSubmitScript(PipelineJob job) throws PipelineJobException
    {
        try
        {
            //is this a reliable location??
            File outDir = job.getLogFile().getParentFile();


            //next, serialize job to XML


            File submitScript = AssayFileWriter.findUniqueFileName("condor.submit", outDir);
            try (FileWriter writer = new FileWriter(submitScript, false))
            {
                //mapping?  assume we use docker?
                writer.write("initialdir=/pipeline/");
                writer.write("executable=" + _config.getRemoteExecutable());

                //NOTE: this is just the output of the java process, so do not put into regular pipeline log
                String basename = FileUtil.getBaseName(job.getLogFile());
                writer.write("output=" + getClusterPath(new File(outDir, basename + "-$(Cluster).$(Process).java.log")));
                writer.write("error=" + getClusterPath(new File(outDir, basename + "-$(Cluster).$(Process).java.log")));
                writer.write("log=" + getClusterPath(new File(outDir, basename + "-$(Cluster).$(Process).condor.log")));

                if (_config.getRequestCpus() != null)
                    writer.write("request_cpus = " + _config.getRequestCpus());
                if (_config.getRequestMemory() != null)
                    writer.write("request_memory = " + _config.getRequestMemory());

                writer.write("getenv = True");

                for (String line : _config.getExtraSubmitLines())
                {
                    writer.write(line);
                }

                writer.write("arguments = '" + StringUtils.join(_config.getJobArgs(outDir, job.getLogFile()), "' '") + "'");
                writer.write("queue 1");
            }

            return submitScript;

        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    public String getStatus(String jobId) throws PipelineJobException
    {
        updateStatusForAll();

        HTCondorJob job = getCondorJobs(jobId).get(0);
        if (job != null)
        {
            //TODO
        }

        return null;
    }

    public static String getClusterPath(File localFile)
    {
        return getClusterPath(localFile, false);
    }

    public static String getClusterPath(File localFile, boolean asURI)
    {
        String localPath = localFile.getAbsoluteFile().toURI().toString();

        // This PathMapper considers "local" from a cluster node's point of view.
        String ret = null;//PipelineJobService.get().getClusterPathMapper().remoteToLocal(localPath);

        if (ret != null && !asURI)
        {
            return ret.replaceFirst("^file:/", "");
        }

        return ret;
    }

    private File getSerializedJobFile(File statusFile)
    {
        if (statusFile == null)
        {
            return null;
        }

        String name = statusFile.getName();

        // Assume the status file's extension has a single period (e.g. .status or .log),
        // and remove that extension.
        int index = name.lastIndexOf('.');
        if (index != -1)
        {
            name = name.substring(0, index);
        }
        return new File(statusFile.getParentFile(), name + ".job.xml");
    }

    @NotNull
    private List<HTCondorJob> getCondorJobs(String jobId)
    {
        TableInfo ti = HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("jobId"), jobId);

        List<HTCondorJob> condorJobs = new TableSelector(ti, filter, new Sort("-created")).getArrayList(HTCondorJob.class);
        if (condorJobs.isEmpty())
        {
            return Collections.emptyList();
        }

        return condorJobs;
    }

    public void updateStatusForAll()
    {
        String command = _config.getStatusCommandExpr().eval(getBaseCtx());
        String ret = execute(command);
    }

    @Override
    public void cancelJob(String jobId)
    {
        //find condor Id for Job Id
        String condorId = getCondorIdForJobId(jobId);

        Map<String, String> ctx = getBaseCtx();
        ctx.put("condorId", condorId);
        String ret = execute(_config.getRemoveCommandExpr().eval(ctx));

        //TODO: update DB w/ status
    }

    private String getCondorIdForJobId(String jobId)
    {
        List<HTCondorJob> jobs = getCondorJobs(jobId);
        if (jobs.size() > 1)
        {

        }
        else if (jobs.isEmpty())
        {
            return null;
        }

        //TODO: make a status column?
        return jobs.get(0).getClusterId();
    }

    private String execute(String command)
    {
        _log.info("executing HTCondor command: " + command);
        StringBuilder sb = new StringBuilder();

        try
        {
            Process p = Runtime.getRuntime().exec(command);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream())))
            {
                while (in.ready())
                {
                    sb.append(in.readLine());
                }

                p.waitFor();

                _log.info("results: ");
                _log.info(sb.toString());

                return sb.toString();
            }
            catch (InterruptedException e)
            {
                _log.error("error executing command: " + command);
                _log.info(sb.toString());
                _log.error(e.getMessage(), e);
            }
            finally
            {
                if (p != null)
                {
                    p.destroy();
                }
            }
        }
        catch (IOException e)
        {
            _log.error("error executing command: " + command);
            _log.info(sb.toString());
            _log.error(e.getMessage(), e);
        }

        return null;
    }
}
