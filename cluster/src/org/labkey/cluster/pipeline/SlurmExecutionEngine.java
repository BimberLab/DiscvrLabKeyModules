package org.labkey.cluster.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.cluster.ClusterResourceAllocator;
import org.labkey.api.collections.CaseInsensitiveHashSet;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 5/25/2017.
 */
public class SlurmExecutionEngine extends AbstractClusterExecutionEngine<SlurmExecutionEngineConfig>
{
    public static String TYPE = "SlurmEngine";
    protected static final Logger _log = LogManager.getLogger(SlurmExecutionEngine.class);

    public SlurmExecutionEngine(SlurmExecutionEngineConfig config)
    {
        super(config, _log);
    }

    @NotNull
    @Override
    public String getType()
    {
        return TYPE;
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
                if (line.startsWith("Submitted batch job"))
                {
                    line = line.replaceFirst("^Submitted batch job", "");
                    line = line.trim();
                    j.setClusterId(line);

                    break;
                }
            }
        }

        if (j.getClusterId() == null)
        {
            job.getLogger().error("Unable to parse cluster ID: " + StringUtils.join(ret, "\n"));
        }

        return ret;
    }

    @Override
    protected Set<String> updateStatusForAllJobs() throws PipelineJobException
    {
        //first check using squeue, since sacct might not pick up newly submitted jobs
        String command = getConfig().getStatusCommandExpr().eval(getBaseCtx(ContainerManager.getRoot()));
        List<String> ret = execute(command);
        Set<String> jobsUpdated = new HashSet<>();
        if (ret != null)
        {
            //verify success
            boolean headerFound = false;
            for (String line : ret)
            {
                line = StringUtils.trimToNull(line);
                if (line == null)
                {
                    continue;
                }

                if (line.startsWith("JOBID"))
                {
                    headerFound = true;
                    continue;
                }

                if (headerFound)
                {
                    String[] tokens = line.split("( )+");
                    if (tokens.length < 8)
                    {
                        _log.warn("squeue line unexpectedly short: [" + line + "]");
                        continue;
                    }

                    String id = StringUtils.trimToNull(tokens[0]);
                    if (id != null)
                    {
                        ClusterJob j = getClusterSubmission(id);
                        if (j == null)
                        {
                            //it is allowable for the same user to submit jobs outside of LK
                            //_log.error("unable to find slurm submission matching: " + id);
                        }
                        else
                        {
                            Pair<String, String> status = translateSlurmStatusToTaskStatus(StringUtils.trimToNull(tokens[4]));
                            updateJobStatus(status == null ? null : status.first, j, status == null ? null : status.second);
                            jobsUpdated.add(j.getClusterId());
                        }
                    }
                }
            }

            //indicates we never hit the header
            if (!headerFound)
            {
                _log.error("error checking slurm job status:");
                _log.error(StringUtils.join(ret, "\n"));
            }
        }

        return jobsUpdated;
    }

    @Override
    protected Pair<String, String> getStatusForJob(ClusterJob job, Container c)
    {
        Map<String, String> ctx = getBaseCtx(c);
        ctx.put("clusterId", job.getClusterId());
        if (job.getClusterId() == null)
        {
            _log.error("clusterId was null for job: " + job.getRowId() + " / " + job.getStatus());
        }

        String command = getConfig().getHistoryCommandExpr().eval(ctx);
        List<String> ret = execute(command);
        if (ret != null)
        {
            //verify success
            boolean headerFound = false;
            boolean foundJobLine = false;
            LinkedHashSet<String> statuses = new LinkedHashSet<>();
            for (String line : ret)
            {
                line = StringUtils.trimToNull(line);
                if (line == null)
                {
                    continue;
                }

                if (line.startsWith("JobID"))
                {
                    foundJobLine = true;
                }
                else if (foundJobLine && line.startsWith("------------"))
                {
                    headerFound = true;
                }
                else if (headerFound)
                {
                    String[] tokens = line.split("( )+");
                    if (tokens.length < 6)
                    {
                        _log.error("sacct line unexpectedly short: [" + line + "]");
                        _log.error("command: " + command);
                        continue;
                    }
                    int statusIdx = tokens.length == 7 ? 5 : 4;

                    String id = StringUtils.trimToNull(tokens[0]);
                    if (id.equals(job.getClusterId()))
                    {
                        statuses.add(StringUtils.trimToNull(tokens[statusIdx]));
                    }
                }
            }

            //NOTE: in the situation where a job is evicted and is then cancelled while waiting, we need to inspect more lines to verify whether this job is actually cancelled
            if (!statuses.isEmpty())
            {
                String status = statuses.stream().skip(statuses.size()-1).findFirst().get();
                if (statuses.size() > 1)
                {
                    _log.error("more than one status returned for job " + job.getClusterId() + ": " + StringUtils.join(statuses, ";") + ", using: " + status);
                }

                return translateSlurmStatusToTaskStatus(status);
            }
        }

        //if not found in condor_history, it could mean it is sitting in the queue
        Pair<String, String> status = getStatusFromQueue(job.getClusterId());
        if (status != null)
        {
            return status;
        }

        checkForCompletedJob(job);


        //indicates we never found status
        _log.error("Error checking slurm job status for job: " + job.getClusterId());
        _log.error(StringUtils.join(ret, "\n"));

        return null;
    }

    @Override
    protected boolean removeJob(ClusterJob clusterJob)
    {
        Map<String, String> ctx = getBaseCtx(ContainerManager.getForId(clusterJob.getContainer()));
        ctx.put("clusterId", clusterJob.getClusterId());
        List<String> ret = execute(getConfig().getRemoveCommandExpr().eval(ctx));
        boolean success = true;
        if (ret != null)
        {
            //verify success.  there does not actually appear to be any output on success
            for (String line : ret)
            {
                line = line.trim();
                if (line.isEmpty())
                {
                    continue;
                }

                if (line.contains("error"))
                {
                    success = false;
                    break;
                }
            }
        }

        if (!success)
        {
            _log.error("error removing slurm job: [" + clusterJob.getJobId() + "]");
            _log.error(StringUtils.join(ret, "\n"));
        }

        return success;
    }

    private File createSubmitScript(PipelineJob job) throws PipelineJobException
    {
        try
        {
            File serializedJobFile = writeJobToFile(job);

            //we want this unique for each task, but reused if submitted multiple times
            File outDir = job.getLogFile().getParentFile();
            String basename = FileUtil.getBaseName(job.getLogFile());
            File submitScript = new File(outDir, basename + (job.getActiveTaskId() == null ? "" : "." + job.getActiveTaskId().getNamespaceClass().getSimpleName()) + ".slurm.sh");
            if (!submitScript.exists())
            {
                try (FileWriter writer = new FileWriter(submitScript, false))
                {
                    writer.write("#!/bin/bash" + "\n");
                    writer.write("#" + "\n");
                    writer.write("#SBATCH --job-name=" + job.getJobGUID() + "\n");
                    writer.write("#SBATCH --ntasks=1\n");
                    writer.write("#SBATCH --get-user-env\n");

                    //NOTE: changing behavior.  instead of requeue, these will now be converted to cancelled
                    //writer.write("#SBATCH --requeue\n");

                    //NOTE: this is just the output of the java process, so do not put into regular pipeline log
                    writer.write("#SBATCH --output=" + getConfig().getClusterPath(new File(outDir, basename + "-%j.java.log")) + "\n");
                    writer.write("#SBATCH --error=" + getConfig().getClusterPath(new File(outDir, basename + "-%j.java.log")) + "\n");

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
                        writer.write("#SBATCH --cpus-per-task=" + cpus + "\n");
                    }

                    Integer ram = null;
                    if (maxRam != null || getConfig().getRequestMemory() != null)
                    {
                        //NOTE: see comment above for CPUs
                        //Also, add buffer between the amount allocated for the slurm job and the amount set in LK.
                        //slurm is more aggressive about killed over memory jobs
                        ram = maxRam != null ? maxRam : getConfig().getRequestMemory();
                        writer.write("#SBATCH --mem=" + (ram + 2) + "000\n");
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
                        writer.write("#SBATCH --export=" + StringUtils.join(environment, ",") + "\n");
                    }

                    for (String line : extraLines)
                    {
                        job.getLogger().debug("adding line to submit script: [" + line + "]");
                        writer.write(line + "\n");
                    }

                    writer.write("#SBATCH --workdir=" + getConfig().getClusterPath(job.getLogFile().getParentFile()) + "\n");

                    String args = StringUtils.join(getConfig().getJobArgs(outDir, serializedJobFile, job, this), " ");
                    writer.write("srun " + getConfig().getRemoteExecutable() + " " + args);
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

    private Pair<String, String> translateSlurmStatusToTaskStatus(String status)
    {
        if (status == null)
            return null;

        try
        {
            StatusType st = StatusType.parseValue(status);
            return Pair.of(st.getLabkeyStatus().toUpperCase(), st.getInfo());
        }
        catch (IllegalArgumentException e)
        {
            _log.error("Unknown status type: [" + status + "]");
        }

        return Pair.of(status, null);
    }

    public static enum StatusType
    {
        BF("Error", PipelineJob.TaskStatus.error, Arrays.asList("BOOT_FAIL")),
        CA("Cancelled", PipelineJob.TaskStatus.cancelled),
        CD("Complete", PipelineJob.TaskStatus.complete, Arrays.asList("Completed")),
        CF("Submitted, Idle", PipelineJob.TaskStatus.waiting, Arrays.asList("CONFIGURING")),
        CG("Running", PipelineJob.TaskStatus.running, Arrays.asList("COMPLETING")),
        F("Failed", PipelineJob.TaskStatus.error),
        NF("Failed", PipelineJob.TaskStatus.error, Arrays.asList("NODE_FAIL")),
        PD("Submitted, Idle", PipelineJob.TaskStatus.waiting, Arrays.asList("PENDING")),
        PR("Preempted", PipelineJob.TaskStatus.waiting, null, "Job preempted"),
        R("Running", PipelineJob.TaskStatus.running),
        SE("Error", PipelineJob.TaskStatus.error, Arrays.asList("SPECIAL_EXIT")),
        ST("Stopped", PipelineJob.TaskStatus.error),
        S("Suspended", PipelineJob.TaskStatus.waiting, null, "Job suspended"),
        TO("Timeout", PipelineJob.TaskStatus.error, null, "Job timeout");

        private Set<String> _aliases = new CaseInsensitiveHashSet();
        private String _labkeyStatus;
        private String _info;
        private PipelineJob.TaskStatus _taskStatus;

        StatusType(String labkeyStatus, PipelineJob.TaskStatus taskStatus)
        {
            this(labkeyStatus, taskStatus, null);
        }

        StatusType(String labkeyStatus, PipelineJob.TaskStatus taskStatus, List<String> aliases)
        {
            this(labkeyStatus, taskStatus, aliases, null);
        }

        StatusType(String labkeyStatus, PipelineJob.TaskStatus taskStatus, List<String> aliases, String info)
        {
            _labkeyStatus = labkeyStatus;
            _info = info;
            _taskStatus = taskStatus;

            if (aliases != null)
            {
                _aliases.addAll(aliases);
            }
        }

        public String getLabkeyStatus()
        {
            return _taskStatus == null ? _labkeyStatus : _taskStatus.name();
        }

        public String getInfo()
        {
            return _info;
        }

        public static StatusType parseValue(String value)
        {
            try
            {
                return valueOf(value);
            }
            catch (IllegalArgumentException e)
            {
                //ignore
            }

            //NOTE: slurm can report a status with '+' after it.
            value = value.replaceAll("\\+", "");
            for (StatusType t : values())
            {
                if (t._labkeyStatus.equalsIgnoreCase(value))
                {
                    return t;
                }
                else if (t._aliases.contains(value))
                {
                    return t;
                }
            }

            throw new IllegalArgumentException("Unknown status value: " + value);
        }
    }

    /**
     * @return The string status, always translated to the LabKey TaskStatus instead of raw condor code
     */
    private Pair<String, String> getStatusFromQueue(String clusterId)
    {
        String command = getConfig().getStatusCommandExpr().eval(getBaseCtx(ContainerManager.getRoot()));
        List<String> ret = execute(command);
        if (ret != null)
        {
            boolean headerFound = false;
            for (String line : ret)
            {
                line = StringUtils.trimToNull(line);
                if (line == null)
                {
                    continue;
                }

                if (line.startsWith("JOBID"))
                {
                    headerFound = true;
                    continue;
                }

                if (headerFound)
                {
                    String[] tokens = line.split("( )+");
                    if (tokens.length < 8)
                    {
                        _log.warn("squeue line unexpectedly short: [" + line + "]");
                        continue;
                    }

                    String id = StringUtils.trimToNull(tokens[0]);
                    if (clusterId.equals(id))
                    {
                        return translateSlurmStatusToTaskStatus(StringUtils.trimToNull(tokens[4]));
                    }
                }
            }

            //indicates we never hit the header
            if (!headerFound)
            {
                _log.error("error checking slurm job status:");
                _log.error(StringUtils.join(ret, "\n"));
            }
        }

        return null;
    }
}
