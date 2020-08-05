package org.labkey.cluster.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.cluster.ClusterResourceAllocator;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.cluster.ClusterServiceImpl;
import org.quartz.JobExecutionException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 10/31/2015.
 */
public class HTCondorExecutionEngine extends AbstractClusterExecutionEngine<HTCondorExecutionEngineConfig>
{
    public static String TYPE = "HTCondorEngine";
    protected static final Logger _log = LogManager.getLogger(HTCondorExecutionEngine.class);

    public HTCondorExecutionEngine(HTCondorExecutionEngineConfig config)
    {
        super(config, _log);
    }

    protected Set<String> updateStatusForAllJobs() throws PipelineJobException
    {
        //first check using condor_q, since condor_history might not pick up newly submitted jobs
        String command = getConfig().getStatusCommandExpr().eval(getBaseCtx(ContainerManager.getRoot()));
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
                            ClusterJob j = getClusterSubmission(id);
                            if (j == null)
                            {
                                //it is allowable for the same user to submit jobs outside of LK
                                //_log.error("unable to find HTCondor submission matching: " + id);
                            }
                            else
                            {
                                String status = translateCondorStatusToTaskStatus(StringUtils.trimToNull(tokens[5]));
                                updateJobStatus(status, j, null);
                                jobsUpdated.add(j.getClusterId());
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

        return jobsUpdated;
    }

    @NotNull
    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    protected List<String> submitJobToCluster(ClusterJob j, PipelineJob job) throws PipelineJobException
    {
        //build submit script
        File submitScript = createSubmitScript(job);

        Map<String, String> ctx = getBaseCtx(job.getContainer());
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
                    if (line.endsWith("."))
                    {
                        line = line + "0";
                    }

                    j.setClusterId(line);

                    break;
                }
                //if --verbose was used
                else if (line.startsWith("** "))
                {
                    line = line.replaceFirst("^\\*\\* Proc ", "");
                    line = line.trim();
                    j.setClusterId(line);

                    break;
                }
            }
        }

        return ret;
    }

    private File createSubmitScript(PipelineJob job) throws PipelineJobException
    {
        try
        {
            File serializedJobFile = writeJobToFile(job);

            //we want this unique for each task, but reused if submitted multiple times
            File outDir = job.getLogFile().getParentFile();
            String basename = FileUtil.getBaseName(job.getLogFile());
            File submitScript = new File(outDir, basename + (job.getActiveTaskId() == null ? "" : "." + job.getActiveTaskId().getNamespaceClass().getSimpleName()) + ".submit");
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
                    List<String> extraLines = new ArrayList<>(getConfig().getExtraSubmitLines());
                    Map<String, Object> extraEnvironmentVars = new HashMap<>();

                    if (job.getActiveTaskId() != null)
                    {
                        List<ClusterResourceAllocator.Factory> allocatorFactories = ClusterServiceImpl.get().getAllocators(job.getActiveTaskId());
                        for (ClusterResourceAllocator.Factory allocatorFact : allocatorFactories)
                        {
                            ClusterResourceAllocator allocator = allocatorFact.getAllocator();
                            job.getLogger().debug("using resource allocator: " + allocator.getClass().getName());
                            Integer c = allocator.getMaxRequestCpus(job);
                            if (c != null)
                            {
                                job.getLogger().debug("setting cpus: " + c);
                                maxCpus = c;
                            }

                            Integer m = allocator.getMaxRequestMemory(job);
                            if (m != null)
                            {
                                job.getLogger().debug("setting memory: " + m);
                                maxRam = m;
                            }

                            allocator.addExtraSubmitScriptLines(job, this, extraLines);

                            extraEnvironmentVars.putAll(allocator.getEnvironmentVars(job, this));
                        }
                    }

                    Integer cpus = null;
                    if (maxCpus != null || getConfig().getRequestCpus() != null)
                    {
                        //NOTE: it is possible this could exceed the max allowable for this cluster.
                        //consider making defaultCpus and maxCpus params
                        cpus = maxCpus != null ? maxCpus : getConfig().getRequestCpus();
                        writer.write("request_cpus = " + cpus + "\n");
                    }

                    Integer ram = null;
                    if (maxRam != null || getConfig().getRequestMemory() != null)
                    {
                        //NOTE: see comment above for CPUs
                        ram = maxRam != null ? maxRam : getConfig().getRequestMemory();
                        writer.write("request_memory = " + ram + " GB\n");
                    }

                    List<String> environment = new ArrayList<>();
                    if (cpus != null)
                    {
                        environment.add("SEQUENCEANALYSIS_MAX_THREADS=" + cpus);
                    }

                    if (ram != null)
                    {
                        environment.add("SEQUENCEANALYSIS_MAX_RAM=" + ram);
                    }

                    if (StringUtils.trimToNull(getConfig().getJavaHome()) != null)
                    {
                        environment.add("JAVA_HOME='" + StringUtils.trimToNull(getConfig().getJavaHome()) + "'");
                    }

                    if (getConfig().getEnvironmentVars() != null && !getConfig().getEnvironmentVars().isEmpty())
                    {
                        environment.addAll(getConfig().getEnvironmentVars());
                    }

                    extraEnvironmentVars.forEach((name, val) -> {
                        environment.add(name + "=" + val);
                    });

                    if (!environment.isEmpty())
                    {
                        writer.write("environment = \"" + StringUtils.join(environment, " ") + "\"\n");
                    }

                    writer.write("getenv = True\n");

                    for (String line : getConfig().getExtraSubmitLines())
                    {
                        writer.write(line + "\n");
                    }

                    for (String line : extraLines)
                    {
                        job.getLogger().debug("adding line to submit script: [" + line + "]");
                        writer.write(line + "\n");
                    }

                    writer.write("arguments = \"'" + StringUtils.join(getConfig().getJobArgs(outDir, serializedJobFile, job, this), "' '").replaceAll("\"", "\"\"") + "'\"\n");
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

    public static enum StatusType
    {
        U("Unexpanded", null),
        I("Submitted, Idle", PipelineJob.TaskStatus.waiting),
        R("Running", PipelineJob.TaskStatus.running),
        X("Cancelled", PipelineJob.TaskStatus.cancelled),
        C("Complete", PipelineJob.TaskStatus.complete),
        H("Held", null),
        E("Error", PipelineJob.TaskStatus.error),
        S("Suspended", PipelineJob.TaskStatus.waiting);

        private String _labkeyStatus;
        private PipelineJob.TaskStatus _taskStatus;

        StatusType(String labkeyStatus, PipelineJob.TaskStatus taskStatus)
        {
            _labkeyStatus = labkeyStatus;
            _taskStatus = taskStatus;
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
    @Override
    protected Pair<String, String> getStatusForJob(ClusterJob job, Container c)
    {
        Map<String, String> ctx = getBaseCtx(c);
        ctx.put("clusterId", job.getClusterId());
        String command = getConfig().getHistoryCommandExpr().eval(ctx);
        if (command == null)
        {
            throw new IllegalArgumentException("History command was null: " + String.valueOf(getConfig().getHistoryCommandExpr()));
        }

        List<String> ret = execute(command);
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
                        if (!id.equals(job.getClusterId()))
                        {
                            _log.error("incorrect line found when calling condor_history for: " + job.getClusterId());
                            _log.error(line);
                            continue;
                        }

                        String s = translateCondorStatusToTaskStatus(StringUtils.trimToNull(tokens[5]));
                        return s != null ? Pair.of(s, null) : null;
                    }
                }
                else if (line.startsWith("ID "))
                {
                    withinJobs = true;
                }
            }
        }

        //if not found in condor_history, it could mean it is sitting in the queue
        String status = getStatusFromQueue(job.getClusterId());
        if (status != null)
        {
            return Pair.of(status, null);
        }

        checkForCompletedJob(job);

        //indicates we never hit the header
        _log.error("Error checking htcondor job status for job: " + job.getClusterId());
        _log.error(StringUtils.join(ret, "\n"));

        return null;
    }

    /**
     * @return The string status, always translated to the LabKey TaskStatus instead of raw condor code
     */
    private String getStatusFromQueue(String clusterId)
    {
        String command = getConfig().getStatusCommandExpr().eval(getBaseCtx(ContainerManager.getRoot()));
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
                        if (clusterId.equals(id))
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

    @Override
    public void doScheduledUpdate() throws JobExecutionException
    {
        try
        {
            updateStatusForAll();
            requeueBlockedJobs();
        }
        catch (PipelineJobException ex)
        {
            throw new JobExecutionException(ex.getMessage(), ex);
        }
    }

    @Override
    protected boolean removeJob(ClusterJob clusterJob)
    {
        Map<String, String> ctx = getBaseCtx(ContainerManager.getForId(clusterJob.getContainer()));
        ctx.put("clusterId", clusterJob.getClusterId());
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

                if (line.startsWith("Job " + clusterJob.getClusterId() + " marked for removal"))
                {
                    success = true;
                    break;
                }
            }
        }

        if (!success)
        {
            _log.error("error removing htcondor job: [" + clusterJob.getJobId() + "]");
            _log.error(StringUtils.join(ret, "\n"));
        }

        return success;
    }
}