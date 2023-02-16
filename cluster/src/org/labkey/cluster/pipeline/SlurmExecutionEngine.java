package org.labkey.cluster.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cluster.ClusterResourceAllocator;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.cluster.ClusterManager;
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
import java.util.stream.Collectors;

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

        List<String> ret = execute(command, submitScript.getParentFile());
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
        else
        {
            _log.info("No output returned after slurm command: " + command);
        }

        if (j.getClusterId() == null)
        {
            job.getLogger().error("Unable to parse cluster ID: " + StringUtils.join(ret, "\n"));
            job.getLogger().error("Command was: [" + command + "]");
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
            List<String> header = null;
            int jobIdx = -1;
            int stateIdx = -1;
            int hostnameIdx = -1;
            int reasonIdx = -1;
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
                    header = Arrays.asList(line.toUpperCase().split("( )+"));
                    jobIdx = header.indexOf("JOBID");
                    stateIdx = header.indexOf("STATE");
                    hostnameIdx = header.indexOf("NODELIST");
                    reasonIdx = header.indexOf("REASON");

                    if (stateIdx == -1)
                    {
                        _log.error("Unable to find STATE in header: " + StringUtils.join(header, ", "));
                        break;
                    }

                    if (jobIdx == -1)
                    {
                        _log.error("Unable to find JOBID in header: " + StringUtils.join(header, ", "));
                        break;
                    }

                    continue;
                }

                if (headerFound)
                {
                    try
                    {
                        String[] tokens = line.split("( )+");
                        String id = StringUtils.trimToNull(tokens[jobIdx]);
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
                                String hostname = hostnameIdx != -1 && tokens.length > hostnameIdx ? StringUtils.trimToNull(tokens[hostnameIdx]) : null;
                                if (hostname != null)
                                {
                                    j.setHostname(hostname);
                                }

                                Pair<String, String> status = translateSlurmStatusToTaskStatus(StringUtils.trimToNull(tokens[stateIdx]));

                                String reason = reasonIdx != -1 && tokens.length > reasonIdx ? StringUtils.trimToNull(tokens[reasonIdx]) : null;
                                if (reason != null)
                                {
                                    if (!"Priority".equals(reason) && !"None".equals(reason))
                                    {
                                        if (status == null)
                                        {
                                            status = new Pair<>("ERROR", null);
                                        }

                                        status.second = "Reason: " + reason;
                                    }
                                }

                                updateJobStatus(status == null ? null : status.first, j, status == null ? null : status.second);
                                jobsUpdated.add(j.getClusterId());
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        _log.error("Error parsing line: " + line, e);
                        throw e;
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
        String info = null;
        List<String> ret = execute(command);
        if (ret != null)
        {
            //verify success
            boolean headerFound = false;
            boolean foundJobLine = false;
            LinkedHashSet<String> statuses = new LinkedHashSet<>();
            List<String> header;
            int jobIdx = -1;
            int stateIdx = -1;
            int hostnameIdx = -1;
            int maxRssIdx = -1;
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
                    header = Arrays.asList(line.toUpperCase().split("( )+"));
                    jobIdx = header.indexOf("JOBID");
                    stateIdx = header.indexOf("STATE");
                    hostnameIdx = header.indexOf("NODELIST");
                    maxRssIdx = header.indexOf("MAXRSS");

                    if (stateIdx == -1)
                    {
                        _log.error("Unable to find STATE in header: " + StringUtils.join(header, ", "));
                        break;
                    }

                    if (jobIdx == -1)
                    {
                        _log.error("Unable to find JOBID in header: " + StringUtils.join(header, ", "));
                        break;
                    }
                }
                else if (foundJobLine && line.startsWith("------------"))
                {
                    headerFound = true;
                }
                else if (headerFound)
                {
                    try
                    {
                        String[] tokens = line.split("( )+");
                        String id = StringUtils.trimToNull(tokens[jobIdx]);
                        if (id.equals(job.getClusterId()))
                        {
                            statuses.add(StringUtils.trimToNull(tokens[stateIdx]));
                        }

                        if (hostnameIdx > -1)
                        {
                            String hostname = tokens.length > hostnameIdx ? StringUtils.trimToNull(tokens[hostnameIdx]) : null;
                            if (hostname != null)
                            {
                                if (job.getHostname() == null || !job.getHostname().equals(hostname))
                                {
                                    job.setHostname(hostname);
                                }
                            }
                        }

                        // NOTE: if the line has blank ending columns, trimmed lines might lack that value
                        if (maxRssIdx > -1 && maxRssIdx < tokens.length)
                        {
                            try
                            {
                                if (NumberUtils.isCreatable(tokens[maxRssIdx]))
                                {
                                    long bytes = FileSizeFormatter.convertStringRepresentationToBytes(tokens[maxRssIdx]);
                                    long requestInBytes = FileSizeFormatter.convertStringRepresentationToBytes(getConfig().getRequestMemory() + "G"); //request is always GB
                                    if (bytes > requestInBytes)
                                    {
                                        info = "Job exceeded memory, max was: " + FileSizeFormatter.convertBytesToUnit(bytes, 'G') + "G";
                                    }
                                }
                            }
                            catch (IllegalArgumentException e)
                            {
                                _log.error("Unable to parse MaxRSS for job: " + job.getClusterId() + ", with line: [" + line + "]", e);
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        _log.error("Error parsing line: " + line, e);
                        throw e;
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

                return translateSlurmStatusToTaskStatus(status, info);
            }
        }

        //if not found in condor_history, it could mean it is sitting in the queue
        Pair<String, String> status = getStatusFromQueue(job);
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

    public static File getExpectedSubmitScript(PipelineJob job)
    {
        String basename = FileUtil.getBaseName(job.getLogFile());
        return new File(job.getLogFile().getParentFile(), basename + (job.getActiveTaskId() == null ? "" : "." + job.getActiveTaskId().getNamespaceClass().getSimpleName()) + ".slurm.sh");
    }

    private File createSubmitScript(PipelineJob job) throws PipelineJobException
    {
        try
        {
            File serializedJobFile = writeJobToFile(job);

            //we want this unique for each task, but reused if submitted multiple times
            File outDir = job.getLogFile().getParentFile();
            String basename = FileUtil.getBaseName(job.getLogFile());
            File submitScript = getExpectedSubmitScript(job);
            if (ClusterManager.get().isRecreateSubmitScriptFile() && submitScript.exists())
            {
                job.getLogger().info("Deleting existing submit script");
                submitScript.delete();
            }

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
                    writer.write("#SBATCH --output=\"" + getConfig().getClusterPath(new File(outDir, basename + "-%j.java.log")) + "\"\n");
                    writer.write("#SBATCH --error=\"" + getConfig().getClusterPath(new File(outDir, basename + "-%j.java.log")) + "\"\n");

                    List<String> disallowedNodes = ClusterManager.get().getDisallowedNodes();
                    if (disallowedNodes != null)
                    {
                        job.getLogger().debug("Disallowed nodes: " + StringUtils.join(ClusterManager.get().getDisallowedNodes(), ","));
                        writer.write("#SBATCH --exclude=" + StringUtils.join(ClusterManager.get().getDisallowedNodes(), ",") + "\n");
                    }

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
                            job.getLogger().debug("using resource allocator: " + allocator.getClass().getName() + " for activeTask: " + job.getActiveTaskId());
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
                        // Adjust based on the java process -Xmx:
                        List<String> javaOpts = getConfig().getFinalJavaOpts(job, this);
                        if (javaOpts != null)
                        {
                            List<Integer> javaOptXms = javaOpts.stream().filter(x -> x.startsWith("-Xmx")).map(x -> x.replaceAll("-Xmx", "").replaceAll("g", "")).map(x -> Integer.parseInt(x)).collect(Collectors.toList());
                            if (!javaOptXms.isEmpty())
                            {
                                if (javaOptXms.size() > 1)
                                {
                                    job.getLogger().error("More than one java -Xmx found: " + StringUtils.join(javaOpts, " "));
                                }

                                job.getLogger().debug("Adjusting request RAM based on java process -Xmx from " + ram + ", reducing by : " + javaOptXms.get(0));
                                ram = ram - javaOptXms.get(0);
                            }
                        }

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

                    // NOTE: --workdir no longer support in sbatch
                    //writer.write("#SBATCH --workdir=\"" + getConfig().getClusterPath(job.getLogFile().getParentFile()) + "\"\n");

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
        return translateSlurmStatusToTaskStatus(status, null);
    }

    private Pair<String, String> translateSlurmStatusToTaskStatus(String status, @Nullable String info)
    {
        if (status == null)
            return null;

        try
        {
            StatusType st = StatusType.parseValue(status);
            return Pair.of(st.getLabkeyStatus().toUpperCase(), st.getInfo(info));
        }
        catch (IllegalArgumentException e)
        {
            _log.error("Unknown status type: [" + status + "]");
        }

        return Pair.of(status, info);
    }

    public enum StatusType
    {
        BF("Error", PipelineJob.TaskStatus.error, List.of("BOOT_FAIL")),
        CA("Cancelled", PipelineJob.TaskStatus.cancelled),
        CD("Complete", PipelineJob.TaskStatus.complete, List.of("Completed")),
        CF("Submitted, Idle", PipelineJob.TaskStatus.waiting, List.of("CONFIGURING")),
        CG("Running", PipelineJob.TaskStatus.running, List.of("COMPLETING")),
        F("Failed", PipelineJob.TaskStatus.error),
        NF("Failed", PipelineJob.TaskStatus.error, List.of("NODE_FAIL")),
        PD("Submitted, Idle", PipelineJob.TaskStatus.waiting, List.of("PENDING")),
        PR("Preempted", PipelineJob.TaskStatus.waiting, null, "Job preempted"),
        R("Running", PipelineJob.TaskStatus.running),
        SE("Error", PipelineJob.TaskStatus.error, List.of("SPECIAL_EXIT")),
        ST("Stopped", PipelineJob.TaskStatus.error),
        S("Suspended", PipelineJob.TaskStatus.waiting, null, "Job suspended"),
        TO("Timeout", PipelineJob.TaskStatus.error, null, "Job timeout"),
        OOM("Out of Memory", PipelineJob.TaskStatus.error, Arrays.asList("OUT_OF_MEMORY", "OUT_OF_ME"), "Out of Memory");

        private final Set<String> _aliases = new CaseInsensitiveHashSet();
        private final String _labkeyStatus;
        private final String _info;
        private final PipelineJob.TaskStatus _taskStatus;

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

        public String getInfo(@Nullable String info)
        {
            return _info == null ? info : _info + (info == null ? "" : " / " + info);
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
    private Pair<String, String> getStatusFromQueue(ClusterJob job)
    {
        String command = getConfig().getStatusCommandExpr().eval(getBaseCtx(ContainerManager.getRoot()));
        List<String> ret = execute(command);
        if (ret != null)
        {
            boolean headerFound = false;
            List<String> header = null;
            int jobIdx = -1;
            int stateIdx = -1;
            int hostnameIdx = -1;

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
                    header = Arrays.asList(line.toUpperCase().split("( )+"));
                    jobIdx = header.indexOf("JOBID");
                    stateIdx = header.indexOf("STATE");
                    hostnameIdx = header.indexOf("NODELIST");

                    if (stateIdx == -1)
                    {
                        _log.error("Unable to find STATE in header: " + StringUtils.join(header, ", "));
                        break;
                    }

                    if (jobIdx == -1)
                    {
                        _log.error("Unable to find JOBID in header: " + StringUtils.join(header, ", "));
                        break;
                    }

                    continue;
                }

                if (headerFound)
                {
                    try
                    {
                        String[] tokens = line.split("( )+");
                        String id = StringUtils.trimToNull(tokens[jobIdx]);
                        if (job.getClusterId().equals(id))
                        {
                            if (hostnameIdx > -1)
                            {
                                String hostname = tokens.length > hostnameIdx ? StringUtils.trimToNull(tokens[hostnameIdx]) : null;
                                if (hostname != null)
                                {
                                    job.setHostname(hostname);
                                }
                            }

                            return translateSlurmStatusToTaskStatus(StringUtils.trimToNull(tokens[stateIdx]));
                        }
                    }
                    catch (Exception e)
                    {
                        _log.error("Error parsing line: " + line, e);
                        throw e;
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

    // Based on: https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java
    private static class FileSizeFormatter
    {
        public static long convertStringRepresentationToBytes(final String value)
        {
            try
            {
                char unit = value.toUpperCase().charAt(value.length() - 1);
                long sizeFactor = getSizeFactor(unit);
                long size = Long.parseLong(value.substring(0, value.length() - 1));

                return size * sizeFactor;
            }
            catch (Exception e)
            {
                throw new IllegalArgumentException("Improper size string: " + value, e);
            }
        }

        public static long convertBytesToUnit(final long bytes, final char unit)
        {
            long sizeFactor = getSizeFactor(unit);

            return bytes / sizeFactor;
        }

        private static long getSizeFactor(char unit)
        {
            final long K = 1024;
            final long M = K * K;
            final long G = M * K;
            final long T = G * K;

            return switch (unit)
                    {
                        case 'K' -> K;
                        case 'M' -> M;
                        case 'G' -> G;
                        case 'T' -> T;
                        default -> 1;
                    };
        }
    }

    public static class TestCase
    {
        @Test
        public void testFileSizeFormatter()
        {
            long bytes = FileSizeFormatter.convertStringRepresentationToBytes("1362624K");
            Assert.assertEquals("Incorrect byte value", 1395326976, bytes);

            long val2 = FileSizeFormatter.convertBytesToUnit(bytes, 'K');
            Assert.assertEquals("Incorrect string value", 1362624, val2);
        }
    }
}
