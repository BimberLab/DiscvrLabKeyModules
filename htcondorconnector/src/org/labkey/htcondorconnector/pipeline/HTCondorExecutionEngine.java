package org.labkey.htcondorconnector.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.htcondorconnector.HTCondorJobResourceAllocator;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.htcondorconnector.HTCondorConnectorManager;
import org.labkey.htcondorconnector.HTCondorConnectorModule;
import org.labkey.htcondorconnector.HTCondorConnectorSchema;
import org.labkey.htcondorconnector.HTCondorServiceImpl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 10/31/2015.
 */
public class HTCondorExecutionEngine implements RemoteExecutionEngine<HTCondorExecutionEngineConfig>
{
    private static final Logger _log = Logger.getLogger(HTCondorExecutionEngine.class);
    public static String TYPE = "HTCondorEngine";
    private static final String PREPARING = "PREPARING";
    private static final String NOT_SUBMITTED = "NOT_SUBMITTED";

    //TODO: allow a site param to set this
    private boolean _debug = false;

    private HTCondorExecutionEngineConfig _config;

    public HTCondorExecutionEngine(HTCondorExecutionEngineConfig config)
    {
        _config = config;
    }

    @NotNull
    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public HTCondorExecutionEngineConfig getConfig()
    {
        return _config;
    }

    @Override
    public void submitJob(PipelineJob job) throws PipelineJobException
    {
        //check to avoid duplicate submissions
        HTCondorJob existingSubmission = getMostRecentCondorSubmission(job.getJobGUID());
        if (existingSubmission != null)
        {
            //this means we have a duplicate
            if (job.getActiveTaskId() != null && job.getActiveTaskId().toString().equals(existingSubmission.getActiveTaskId()))
            {
                job.getLogger().warn("duplicate submission attempt, skipping.  original condor id: " + existingSubmission.getCondorId());
                return;
            }
        }

        //first create placeholder in DB
        HTCondorJob j = new HTCondorJob();
        j.setContainer(job.getContainer().getId());
        j.setCreated(new Date());
        j.setCreatedBy(job.getUser().getUserId());
        j.setModified(new Date());
        j.setModifiedBy(job.getUser().getUserId());
        j.setJobId(job.getJobGUID());
        j.setActiveTaskId(job.getActiveTaskId() == null ? null : job.getActiveTaskId().toString());
        j.setLocation(getConfig().getLocation());
        j.setStatus(PREPARING);
        j = Table.insert(job.getUser(), HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS), j);

        if (j.getRowId() == 0)
        {
            _log.error("rowid not set on HTCondorJob");
            return;
        }

        if (HTCondorConnectorManager.get().isPreventNewJobs())
        {
            job.getLogger().info("submission to HTCondor has been disabled.  will resubmit when this is enabled");
            job.setStatus(PipelineJob.TaskStatus.waiting, "HTCondor submission disabled");
            j.setStatus(NOT_SUBMITTED);
            Table.update(job.getUser(), HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS), j, j.getRowId());
        }
        else
        {
            submitJobToCondor(j, job);
        }
    }

    private void submitJobToCondor(HTCondorJob j, PipelineJob job) throws PipelineJobException
    {
        boolean success = false;
        try
        {
            //build submit script
            File submitScript = createSubmitScript(job);

            Map<String, String> ctx = getBaseCtx();
            ctx.put("submitScript", getConfig().getClusterPath(submitScript));
            String command = getConfig().getSubmitCommandExpr().eval(ctx);

            List<String> ret = execute(command);
            if (ret != null)
            {
                //verify success; create job
                for (String line : ret)
                {
                    //the normal output
                    if (line.startsWith("1 job(s) submitted to cluster"))
                    {
                        line = line.replaceFirst("^1 job\\(s\\) submitted to cluster", "");
                        line = line.trim();
                        String[] tokens = line.split("\\.");
                        if (tokens.length > 1 && tokens[1].isEmpty())
                        {
                            tokens[1] = "0";
                        }

                        j.setClusterId(tokens[0]);
                        j.setProcessId(tokens.length > 1 ? tokens[1] : "0");

                        break;
                    }
                    //if --verbose was used
                    else if (line.startsWith("** "))
                    {
                        line = line.replaceFirst("^\\*\\* Proc ", "");
                        line = line.trim();
                        String[] tokens = line.split("\\.");

                        j.setClusterId(tokens[0]);
                        j.setProcessId(tokens[1]);

                        break;
                    }
                }
            }

            if (j.getClusterId() != null)
            {
                j.setStatus("SUBMITTED");
                Table.update(job.getUser(), HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS), j, j.getRowId());
                job.setStatus("SUBMITTED");
                job.getLogger().info("Submitted to HTCondor with jobId: " + j.getCondorId());
                job.getLogger().debug("jobGuid: " + job.getJobGUID());
                job.getLogger().debug("active task: " + job.getActiveTaskId());
                success = true;
            }
            else
            {
                job.getLogger().error("Error submitting job to HTCondor:");
                job.getLogger().error(StringUtils.join(ret, "\n"));
                job.setActiveTaskStatus(PipelineJob.TaskStatus.error);
            }
        }
        finally
        {
            if (!success)
            {
                Table.delete(HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS), j.getRowId());
            }
        }
    }

    public boolean isDebug()
    {
        return _debug;
    }

    public void setDebug(boolean debug)
    {
        _debug = debug;
    }

    private Map<String, String> getBaseCtx()
    {
        Map<String, String> map = new HashMap<>();
        map.put("condorUser", getConfig().getCondorUser());

        return map;
    }

    private File createSubmitScript(PipelineJob job) throws PipelineJobException
    {
        try
        {
            File outDir = job.getLogFile().getParentFile();

            //next, serialize job to XML.  deleting any existing file which might be from a previous task
            File serializedJobFile = getSerializedJobFile(job.getLogFile());
            if (NetworkDrive.exists(serializedJobFile))
            {
                _log.info("job XML already exists, deleting");
                serializedJobFile.delete();
            }

            job.writeToFile(serializedJobFile);

            //we want this unique for each task, but reused if submitted multiple times
            String basename = FileUtil.getBaseName(job.getLogFile());
            File submitScript = new File(outDir, basename + "." + (job.getActiveTaskId().getNamespaceClass().getSimpleName()) + ".submit");
            if (!submitScript.exists())
            {
                try (FileWriter writer = new FileWriter(submitScript, false))
                {
                    writer.write("initialdir=" + getConfig().getClusterPath(job.getLogFile().getParentFile()) + "\n");
                    writer.write("executable=" + getConfig().getRemoteExecutable() + "\n");

                    //NOTE: this is just the output of the java process, so do not put into regular pipeline log
                    writer.write("output=" + getConfig().getClusterPath(new File(outDir, basename + "-$(Cluster).$(Process).java.log")) + "\n");
                    writer.write("error=" + getConfig().getClusterPath(new File(outDir, basename + "-$(Cluster).$(Process).java.log")) + "\n");
                    writer.write("log=" + getConfig().getClusterPath(new File(outDir, basename + "-$(Cluster).$(Process).condor.log")) + "\n");

                    // This allows modules to register code to modify resource usage per task.
                    Integer maxCpus = null;
                    Integer maxRam = null;
                    List<String> extraLines = null;

                    if (job.getActiveTaskId() != null)
                    {
                        HTCondorJobResourceAllocator.Factory allocatorFact = HTCondorServiceImpl.get().getAllocator(job.getActiveTaskId());
                        if (allocatorFact != null)
                        {
                            HTCondorJobResourceAllocator allocator = allocatorFact.getAllocator();
                            job.getLogger().debug("using resource allocator: " + allocator.getClass().getName());
                            maxCpus = allocator.getMaxRequestCpus(job);
                            maxRam = allocator.getMaxRequestMemory(job);
                            extraLines = allocator.getExtraSubmitScriptLines(job);
                        }
                    }

                    Integer cpus = null;
                    if (getConfig().getRequestCpus() != null)
                    {
                        //NOTE: it is possible this could exceed the max allowable for this cluster.
                        //consider making defaultCpus and maxCpus params
                        cpus = maxCpus != null ? maxCpus : getConfig().getRequestCpus();
                        writer.write("request_cpus = " + cpus + "\n");
                    }

                    Integer ram = null;
                    if (getConfig().getRequestMemory() != null)
                    {
                        //NOTE: see comment above for CPUs
                        ram = maxRam != null ? maxRam : getConfig().getRequestMemory();
                        writer.write("request_memory = " + ram + " GB\n");
                    }

                    if (StringUtils.trimToNull(getConfig().getJavaHome()) != null)
                    {
                        String suffix = "";
                        if (getConfig().getEnvironmentVars() != null && !getConfig().getEnvironmentVars().isEmpty())
                        {
                            suffix += " " + StringUtils.join(getConfig().getEnvironmentVars(), " ");
                        }
                        writer.write("environment = \"" + (cpus == null ? "" : "SEQUENCEANALYSIS_MAX_THREADS=" + cpus + " ") + (ram == null ? "" : "SEQUENCEANALYSIS_MAX_RAM=" + ram + " ") + "JAVA_HOME='" + StringUtils.trimToNull(getConfig().getJavaHome()) + "'" + (suffix) + "\"\n");
                    }
                    writer.write("getenv = True\n");

                    for (String line : getConfig().getExtraSubmitLines())
                    {
                        writer.write(line + "\n");
                    }

                    if (extraLines != null)
                    {
                        for (String line : extraLines)
                        {
                            job.getLogger().debug("adding line to submit script: [" + line + "]");
                            writer.write(line + "\n");
                        }
                    }

                    writer.write("arguments = \"'" + StringUtils.join(getConfig().getJobArgs(outDir, serializedJobFile), "' '").replaceAll("\"", "\"\"") + "'\"\n");
                    writer.write("queue 1");
                }
            }
            else
            {
                job.getLogger().debug("existing submit script found, reusing");
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
        String ret = null;
        HTCondorJob job = getMostRecentCondorSubmission(jobId);
        if (job != null)
        {
            //if the job was blocked from condor submission, treat as though queued
            if (NOT_SUBMITTED.equals(job.getStatus()))
            {
                return PipelineJob.TaskStatus.waiting.name();
            }

            ret = getStatusForJob(job.getCondorId());
        }

        return ret == null ? "UNKNOWN" : ret;
    }

    private File getSerializedJobFile(File statusFile)
    {
        if (statusFile == null)
        {
            return null;
        }

        String name = FileUtil.getBaseName(statusFile.getName());

        return new File(statusFile.getParentFile(), name + ".job.xml");
    }

    @NotNull
    private List<HTCondorJob> getCondorSubmissionsForJob(String jobId)
    {
        TableInfo ti = HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("jobId"), jobId);
        filter.addCondition(FieldKey.fromString("location"), getConfig().getLocation());
        filter.addCondition(FieldKey.fromString("clusterId"), null, CompareType.NONBLANK);
        filter.addCondition(FieldKey.fromString("status"), PipelineJob.TaskStatus.cancelled.name().toUpperCase(), CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("status"), PipelineJob.TaskStatus.error.name().toUpperCase(), CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("status"), PipelineJob.TaskStatus.complete.name().toUpperCase(), CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("status"), PREPARING, CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("status"), NOT_SUBMITTED, CompareType.NEQ_OR_NULL);

        List<HTCondorJob> condorJobs = new TableSelector(ti, filter, new Sort("-created")).getArrayList(HTCondorJob.class);
        if (condorJobs.isEmpty())
        {
            return Collections.emptyList();
        }

        return condorJobs;
    }

    @NotNull
    private HTCondorJob getCondorSubmission(String condorId)
    {
        TableInfo ti = HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("condorId"), condorId);
        filter.addCondition(FieldKey.fromString("location"), getConfig().getLocation());

        List<HTCondorJob> ret = new TableSelector(ti, filter, new Sort("-created")).getArrayList(HTCondorJob.class);
        if (ret.size() > 1)
        {
            _log.error("multiple HTCondor submissions exist for same id: " + condorId);
        }

        return ret.isEmpty() ? null : ret.get(0);
    }

    public static enum StatusType
    {
        U(0, "Unexpanded", null),
        I(1, "Submitted, Idle", PipelineJob.TaskStatus.waiting),
        R(2, "Running", PipelineJob.TaskStatus.running),
        X(3, "Cancelled", PipelineJob.TaskStatus.cancelled),
        C(4, "Complete", PipelineJob.TaskStatus.complete),
        H(5, "Held", null),
        E(6, "Error", PipelineJob.TaskStatus.error);

        private int _code;
        private String _labkeyStatus;
        private PipelineJob.TaskStatus _taskStatus;

        StatusType(int code, String labkeyStatus, PipelineJob.TaskStatus taskStatus)
        {
            _code = code;
            _labkeyStatus = labkeyStatus;
            _taskStatus = taskStatus;
        }

        public int getCode()
        {
            return _code;
        }

        public String getLabkeyStatus()
        {
            return _taskStatus == null ? _labkeyStatus : _taskStatus.name();
        }

        public PipelineJob.TaskStatus getTaskStatus()
        {
            return _taskStatus;
        }
    }

    /**
     * @return The string status, always translated to the LabKey TaskStatus instead of raw condor code
     */
    public String getStatusForJob(String condorId)
    {
        Map<String, String> ctx = getBaseCtx();
        ctx.put("condorId", condorId);
        List<String> ret = execute(getConfig().getHistoryCommandExpr().eval(ctx));
        if (ret != null)
        {
            //verify success
            boolean withinJobs = false;
            for (String line : ret)
            {
                line = StringUtils.trimToNull(line);
                if (line == null)
                {
                    continue;
                }

                if (withinJobs)
                {
                    if (line.matches("^[0-9]+ jobs.*"))
                    {
                        break;
                    }
                    else
                    {
                        String[] tokens = line.split("( )+");
                        if (tokens.length < 6)
                        {
                            _log.warn("condor_history line unexpectedly short: [" + line + "]");
                            continue;
                        }

                        String id = StringUtils.trimToNull(tokens[0]);
                        if (!id.equals(condorId))
                        {
                            _log.error("incorrect line found when calling condor_history for: " + condorId);
                            _log.error(line);
                            continue;
                        }

                        return translateCondorStatusToTaskStatus(StringUtils.trimToNull(tokens[5]));
                    }
                }
                else if (line.startsWith("ID "))
                {
                    withinJobs = true;
                }
            }
        }

        //if not found in condor_history, it could mean it is sitting in the queue
        String status = getStatusFromQueue(condorId);
        if (status != null)
        {
            return status;
        }

        //indicates we never hit the header
        _log.error("Error checking htcondor job status for job: " + condorId);
        _log.error(StringUtils.join(ret, "\n"));

        return null;
    }

    /**
     * @return The string status, always translated to the LabKey TaskStatus instead of raw condor code
     */
    private String getStatusFromQueue(String condorId)
    {
        String command = getConfig().getStatusCommandExpr().eval(getBaseCtx());
        List<String> ret = execute(command);
        if (ret != null)
        {
            boolean withinJobs = false;
            for (String line : ret)
            {
                line = StringUtils.trimToNull(line);
                if (line == null)
                {
                    continue;
                }

                if (withinJobs)
                {
                    if (line.matches("^[0-9]+ jobs.*"))
                    {
                        break;
                    }
                    else
                    {
                        String[] tokens = line.split("( )+");
                        if (tokens.length < 6)
                        {
                            _log.warn("condor_q line unexpectedly short: [" + line + "]");
                            continue;
                        }

                        String id = StringUtils.trimToNull(tokens[0]);
                        if (condorId.equals(id))
                        {
                            return translateCondorStatusToTaskStatus(StringUtils.trimToNull(tokens[5]));
                        }
                    }
                }
                else if (line.startsWith("ID "))
                {
                    withinJobs = true;
                }
            }

            //indicates we never hit the header
            if (!withinJobs)
            {
                _log.error("error checking htcondor job status:");
                _log.error(StringUtils.join(ret, "\n"));
            }
        }

        return null;
    }

    public synchronized void requeueBlockedJobs() throws PipelineJobException
    {
        if (!HTCondorConnectorManager.get().isPreventNewJobs())
        {
            //first see if we have any submissions to check
            TableInfo ti = HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("status"), NOT_SUBMITTED, CompareType.EQUAL);
            TableSelector ts = new TableSelector(ti, filter, null);
            List<HTCondorJob> jobs = ts.getArrayList(HTCondorJob.class);
            if (jobs.isEmpty())
            {
                return;
            }

            for (HTCondorJob j : jobs)
            {
                PipelineJob job = PipelineJobService.get().getJobStore().getJob(j.getJobId());
                if (job == null)
                {
                    _log.error("unable to find PipelineJob matching: " + j.getJobId());
                }
                else
                {
                    submitJobToCondor(j, job);
                }
            }
        }
    }

    public synchronized void updateStatusForAll() throws PipelineJobException
    {
        //first see if we have any submissions to check
        TableInfo ti = HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("status"), PageFlowUtil.set(PipelineJob.TaskStatus.error.name().toUpperCase(), PipelineJob.TaskStatus.complete.name().toUpperCase(), PipelineJob.TaskStatus.cancelled.name().toUpperCase(), PREPARING, NOT_SUBMITTED), CompareType.NOT_IN);
        TableSelector ts = new TableSelector(ti, filter, null);
        List<HTCondorJob> jobs = ts.getArrayList(HTCondorJob.class);

        if (jobs.isEmpty())
        {
            return;
        }

        //first check using condor_q, since condor_history might not pick up newly submitted jobs
        String command = getConfig().getStatusCommandExpr().eval(getBaseCtx());
        List<String> ret = execute(command);
        Set<String> jobsUpdated = new HashSet<>();
        if (ret != null)
        {
            //verify success
            boolean withinJobs = false;
            for (String line : ret)
            {
                line = StringUtils.trimToNull(line);
                if (line == null)
                {
                    continue;
                }

                if (withinJobs)
                {
                    if (line.matches("^[0-9]+ jobs.*"))
                    {
                        break;
                    }
                    else
                    {
                        String[] tokens = line.split("( )+");
                        if (tokens.length < 6)
                        {
                            _log.warn("condor_q line unexpectedly short: [" + line + "]");
                            continue;
                        }

                        String id = StringUtils.trimToNull(tokens[0]);
                        if (id != null)
                        {
                            HTCondorJob j = getCondorSubmission(id);
                            if (j == null)
                            {
                                //it is allowable for the same user to submit jobs outside of LK
                                //_log.error("unable to find HTCondor submission matching: " + id);
                            }
                            else
                            {
                                String status = translateCondorStatusToTaskStatus(StringUtils.trimToNull(tokens[5]));
                                updateJobStatus(status, j);
                                jobsUpdated.add(j.getCondorId());
                            }
                        }
                    }
                }
                else if (line.startsWith("ID "))
                {
                    withinJobs = true;
                }
            }

            //indicates we never hit the header
            if (!withinJobs)
            {
                _log.error("error checking htcondor job status:");
                _log.error(StringUtils.join(ret, "\n"));
            }
        }

        // iterate existing submissions to catch completed tasks and errors
        // regenerate this list in case status has otherwise changed
        jobs = ts.getArrayList(HTCondorJob.class);
        for (HTCondorJob j : jobs)
        {
            if (jobsUpdated.contains(j.getCondorId()))
            {
                continue;
            }

            //check condor_history
            String jobStatus = getStatusForJob(j.getCondorId());
            if (jobStatus != null)
            {
                updateJobStatus(jobStatus, j);
            }
            else
            {
                _log.error("unable to find record of job submission: " + j.getCondorId());
            }
        }
    }

    private String translateCondorStatusToTaskStatus(String status)
    {
        if (status == null)
            return null;

        try
        {
            StatusType st = StatusType.valueOf(status);
            return st.getLabkeyStatus().toUpperCase();
        }
        catch (IllegalArgumentException e)
        {
            _log.error("Unknown status type: [" + status + "]");
        }

        return status;
    }

    /**
     * this expects the status normalized from condor codes to LK TaskStatus
     */
    private void updateJobStatus(@Nullable String status, HTCondorJob j) throws PipelineJobException
    {
        //update DB
        boolean statusChanged = (status != null && !status.equals(j.getStatus()));
        j.setLastStatusCheck(new Date());
        j.setStatus(status);

        //no need to redundantly update PipelineJob
        if (!statusChanged)
        {
            Table.update(null, HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS), j, j.getRowId());
            return;
        }

        //and update the actual PipelineJob
        try
        {
            PipelineStatusFile sf = getStatusFileForSubmission(j);
            if (sf != null && status != null)
            {
                File xml = getSerializedJobFile(new File(sf.getFilePath()));
                if (!xml.exists())
                {
                    throw new PipelineJobException("unable to find pipeline XML file");
                }

                //NOTE: this should read from serialized XML file, not rely on the DB
                PipelineJob pj = PipelineJob.readFromFile(xml);
                if (pj == null)
                {
                    _log.error("unable to create PipelineJob from xml file: " + sf.getRowId());
                    return;
                }

                String jobTaskId = pj.getActiveTaskId() == null ? "" : pj.getActiveTaskId().toString();
                if (!jobTaskId.equals(j.getActiveTaskId()))
                {
                    pj.getLogger().debug("pipeline XML activeTaskId (" + jobTaskId + ") does not match submission record (" + j.getActiveTaskId() + ").  this probably means it progressed tasks.  will not update status");
                    return;
                }

                PipelineJob.TaskStatus taskStatus = null;
                for (PipelineJob.TaskStatus ts : PipelineJob.TaskStatus.values())
                {
                    if (ts.matches(status))
                    {
                        taskStatus = ts;
                        break;
                    }
                }

                if (taskStatus != null)
                {
                    //if the remote job exits w/ a non-zero exit code, condor might still count this as complete.
                    //to differentiate completed w/ error from successful completion, test activeTaskStatus as recorded in the job XML
                    if (taskStatus == PipelineJob.TaskStatus.complete && pj.getActiveTaskStatus() == PipelineJob.TaskStatus.error)
                    {
                        taskStatus = PipelineJob.TaskStatus.error;
                    }

                    pj.getLogger().debug("setting active task status for job: " + j.getCondorId() + " to: " + taskStatus.name() + ". status was: " + pj.getActiveTaskStatus() + " (PipelineJob) /" + sf.getStatus() + " (StatusFile) / activeTaskId: " + (pj.getActiveTaskId() != null ? pj.getActiveTaskId().toString() : "no active task") + ", hostname: " + sf.getActiveHostName());
                    try
                    {
                        if (taskStatus == PipelineJob.TaskStatus.running)
                        {
                            pj.setStatus(taskStatus, sf.getInfo());
                        }
                        else
                        {
                            PipelineService.get().setPipelineJobStatus(pj, taskStatus);
                        }
                    }
                    catch (CancelledException e)
                    {
                        j.setStatus(StatusType.X.getLabkeyStatus());
                    }
                }
                else
                {
                    _log.warn("unknown TaskStatus: [" + status + "], skipping update");

                    //pj.getLogger().debug("setting status for job: " + j.getCondorId() + " to: " + status + ". status was: " + sf.getStatus() + " (StatusFile) / activeTaskId" + (pj.getActiveTaskId() != null ? pj.getActiveTaskId().toString() : "no active task"));
                    //pj.setStatus(status, sf.getInfo());
                }
            }
            else
            {
                _log.error("unable to find statusfile for job: " + j.getCondorId() + ", status: " + j.getStatus());
            }

            Table.update(null, HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS), j, j.getRowId());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private PipelineStatusFile getStatusFileForSubmission(HTCondorJob j)
    {
        Integer rowId = PipelineService.get().getJobId(UserManager.getUser(j.getCreatedBy()), ContainerManager.getForId(j.getContainer()), j.getJobId());
        if (rowId != null)
        {
            return PipelineService.get().getStatusFile(rowId);
        }

        return null;
    }

    @Override
    public void cancelJob(String jobId)
    {
        //find condor Id for Job Id
        HTCondorJob condorJob = getMostRecentCondorSubmission(jobId);
        if (condorJob == null)
        {
            _log.error("unable to find HTCondor submission for jobId: " + jobId);
            return;
        }

        //this means job was never actually submitted to condor, so just mark cancelled
        if (NOT_SUBMITTED.equals(condorJob.getStatus()))
        {
            condorJob.setStatus(PipelineJob.TaskStatus.cancelling.name().toUpperCase());
            Table.update(null, HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS), condorJob, condorJob.getRowId());

            Integer jobIdx = PipelineService.get().getJobId(UserManager.getUser(condorJob.getCreatedBy()), ContainerManager.getForId(condorJob.getContainer()), condorJob.getJobId());
            if (jobIdx != null)
            {
                //if this job was never submitted, the job in the database should be up to date
                PipelineStatusFile sf = PipelineService.get().getStatusFile(jobIdx);
                sf.createJobInstance().setStatus(PipelineJob.TaskStatus.cancelled);
            }
            else
            {
                _log.error("unable to find rowid for PipelineJob with Id: " + jobId);
            }
        }

        Map<String, String> ctx = getBaseCtx();
        ctx.put("condorId", condorJob.getCondorId());
        List<String> ret = execute(getConfig().getRemoveCommandExpr().eval(ctx));
        boolean success = false;
        if (ret != null)
        {
            //verify success
            for (String line : ret)
            {
                line = line.trim();
                if (line.isEmpty())
                {
                    continue;
                }

                if (line.startsWith("Job " + condorJob.getCondorId() + " marked for removal"))
                {
                    success = true;
                    break;
                }
            }
        }

        if (success)
        {
            condorJob.setStatus(PipelineJob.TaskStatus.cancelling.name().toUpperCase());
            Table.update(null, HTCondorConnectorSchema.getInstance().getSchema().getTable(HTCondorConnectorSchema.CONDOR_JOBS), condorJob, condorJob.getRowId());
        }
        else
        {
            _log.error("error removing htcondor job: [" + jobId + "]");
            _log.error(StringUtils.join(ret, "\n"));
        }
    }

    private HTCondorJob getMostRecentCondorSubmission(String jobId)
    {
        List<HTCondorJob> jobs = getCondorSubmissionsForJob(jobId);
        if (jobs.isEmpty())
        {
            return null;
        }

        return jobs.get(0);
    }

    private List<String> execute(String command)
    {
        if (isDebug())
            _log.info("executing HTCondor command: " + command);

        List<String> ret = new ArrayList<>();

        try
        {
            Process p = Runtime.getRuntime().exec(command);
            try
            {
                String output = IOUtils.toString(p.getInputStream());
                String errorOutput = IOUtils.toString(p.getErrorStream());

                p.waitFor();

                if (errorOutput != null)
                {
                    errorOutput = errorOutput.replaceAll("\n\r", "\n");
                    ret.addAll(Arrays.asList(errorOutput.split("\n")));
                }

                if (output != null)
                {
                    output = output.replaceAll("\n\r", "\n");
                    ret.addAll(Arrays.asList(output.split("\n")));
                }

                if (isDebug())
                {
                    _log.info("results: ");
                    _log.info(StringUtils.join(ret, "\n"));
                }

                return ret;
            }
            catch (InterruptedException e)
            {
                _log.error("Error executing HTCondor command: " + command);
                _log.info(StringUtils.join(ret, "\n"));
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
            _log.error("Error executing HTCondor command: " + command);
            _log.info(StringUtils.join(ret, "\n"));
            _log.error(e.getMessage(), e);
        }

        return null;
    }

    @TestTimeout(240)
    public static class TestCase extends Assert
    {
        protected static final Logger _log = Logger.getLogger(TestCase.class);
        private static final String PROJECT_NAME = "HTCondorTestProject";

        @BeforeClass
        public static void initialSetUp() throws Exception
        {
            //pre-clean
            doCleanup(PROJECT_NAME);

            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (project == null)
            {
                project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME);
                Set<Module> modules = new HashSet<>();
                modules.addAll(project.getActiveModules());
                modules.add(ModuleLoader.getInstance().getModule(HTCondorConnectorModule.class));
                project.setFolderType(FolderTypeManager.get().getFolderType("Laboratory Folder"), TestContext.get().getUser());
                project.setActiveModules(modules);
            }
        }

        @AfterClass
        public static void cleanup()
        {
            doCleanup(PROJECT_NAME);
        }

        protected static void doCleanup(String projectName)
        {
            Container project = ContainerManager.getForPath(projectName);
            if (project != null)
            {
                File pipelineRoot = PipelineService.get().getPipelineRootSetting(project).getRootPath();
                try
                {
                    if (pipelineRoot.exists())
                    {
                        File[] contents = pipelineRoot.listFiles();
                        for (File f : contents)
                        {
                            if (f.exists())
                            {
                                if (f.isDirectory())
                                    FileUtils.deleteDirectory(f);
                                else
                                    f.delete();
                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }

                ContainerManager.deleteAll(project, TestContext.get().getUser());
            }
        }

        @Test
        public void basicTest() throws Exception
        {
            Container c = ContainerManager.getForPath(PROJECT_NAME);
            runTestJob(c, TestContext.get().getUser());
        }

        public static void runTestJob(Container c, User u) throws PipelineJobException
        {
            HTCondorExecutionEngine engine = null;
            for (RemoteExecutionEngine e : PipelineJobService.get().getRemoteExecutionEngines())
            {
                if (e instanceof HTCondorExecutionEngine)
                {
                    engine = (HTCondorExecutionEngine)e;
                }
            }

            if (engine == null)
            {
                _log.info("No HTCondorConfig engine, aborting test");
                return;
            }

            boolean orig = engine.isDebug();
            try
            {
                engine.setDebug(true);

                PipeRoot root = PipelineService.get().getPipelineRootSetting(c);

                //submit job
                PipelineJob job1 = new HTCondorTestJob(c, u, root);
                File log1 = new File(root.getRootPath(), "pipeline1.log");
                log1.createNewFile();
                job1.setLogFile(log1);
                engine.submitJob(job1);
                engine.getStatus(job1.getJobGUID());

                //cancel job
                HTCondorTestJob job2 = new HTCondorTestJob(c, u, root);
                job2._sleep = 5000;
                File log2 = new File(root.getRootPath(), "pipeline2.log");
                log2.createNewFile();
                job2.setLogFile(log2);
                engine.submitJob(job2);
                engine.cancelJob(job2.getJobGUID());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            finally
            {
                engine.setDebug(orig);
            }
        }

        private static class HTCondorTestJob extends PipelineJob
        {
            public long _sleep = 0;

            public HTCondorTestJob(Container c, User user, PipeRoot pipeRoot)
            {
                super("HTCondorTestJob", new ViewBackgroundInfo(c, user, null), pipeRoot);

            }

            @Override
            public URLHelper getStatusHref()
            {
                return null;
            }

            @Override
            public String getDescription()
            {
                return "This is a test job for HTCondor";
            }

            @Override
            public void run()
            {
                getLogger().info("Running!");

                if (_sleep > 0)
                {
                    try
                    {
                        Thread.sleep(_sleep);
                    }
                    catch (InterruptedException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}