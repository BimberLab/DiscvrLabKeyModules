package org.labkey.cluster.pipeline;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.cluster.ClusterManager;
import org.labkey.cluster.ClusterSchema;
import org.labkey.cluster.ClusterServiceImpl;
import org.labkey.cluster.RemoteClusterEngine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 7/11/2017.
 */
abstract class AbstractClusterExecutionEngine<ConfigType extends PipelineJobService.RemoteExecutionEngineConfig> implements RemoteClusterEngine, RemoteExecutionEngine<ConfigType>
{
    private Logger _log;
    public static final String PREPARING = "PREPARING";
    public static final String NOT_SUBMITTED = "NOT_SUBMITTED";

    //TODO: allow a site param to set this
    protected boolean _debug = false;

    private ConfigType _config;

    protected AbstractClusterExecutionEngine(ConfigType config, Logger log)
    {
        _config = config;
        _log = log;
    }

    @Override
    public ConfigType getConfig()
    {
        return _config;
    }

    @Override
    public void submitJob(PipelineJob job) throws PipelineJobException
    {
        //check to avoid duplicate submissions
        ClusterJob existingSubmission = getMostRecentClusterSubmission(job.getJobGUID(), false);
        if (existingSubmission != null)
        {
            //this means we have a duplicate
            if (job.getActiveTaskId() != null && job.getActiveTaskId().toString().equals(existingSubmission.getActiveTaskId()))
            {
                job.getLogger().error("duplicate submission attempt, skipping.  original cluster id: " + existingSubmission.getClusterId());
                _log.error("duplicate submission attempt, skipping.  original cluster id: " + existingSubmission.getClusterId() + ", job id: " +  job.getJobGUID());
                return;
            }
        }

        //first create placeholder in DB
        ClusterJob j = new ClusterJob();
        j.setContainer(job.getContainer().getId());
        j.setCreated(new Date());
        j.setCreatedBy(job.getUser().getUserId());
        j.setModified(new Date());
        j.setModifiedBy(job.getUser().getUserId());
        j.setJobId(job.getJobGUID());
        j.setActiveTaskId(job.getActiveTaskId() == null ? null : job.getActiveTaskId().toString());
        j.setLocation(getConfig().getLocation());
        j.setStatus(PREPARING);

        PipelineStatusFile sf = PipelineService.get().getStatusFile(job.getJobGUID());
        if (sf == null)
        {
            _log.error("Unable to find status file for job: " + job.getJobGUID(), new Exception());
        }
        else
        {
            j.setStatusFileId(sf.getRowId());
        }

        j = Table.insert(job.getUser(), ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS), j);

        if (j.getRowId() == 0)
        {
            _log.error("rowid not set on ClusterJob");
            return;
        }

        if (ClusterManager.get().isPreventClusterInteraction())
        {
            job.getLogger().info("submission to cluster has been disabled.  will resubmit when this is enabled");
            job.setStatus(PipelineJob.TaskStatus.waiting, "Cluster submission disabled");
            j.setStatus(NOT_SUBMITTED);
            Table.update(job.getUser(), ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS), j, j.getRowId());
        }
        else
        {
            boolean success = false;
            try
            {
                success = doSubmitJobToCluster(j, job);
            }
            finally
            {
                if (!success)
                {
                    Table.delete(ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS), j.getRowId());
                }
            }
        }
    }

    abstract protected List<String> submitJobToCluster(ClusterJob j, PipelineJob job) throws PipelineJobException;

    public boolean isDebug()
    {
        return _debug;
    }

    public void setDebug(boolean debug)
    {
        _debug = debug;
    }

    protected Map<String, String> getBaseCtx(Container c)
    {
        Map<String, String> map = new HashMap<>();
        map.put("clusterUser", ClusterServiceImpl.get().getClusterUser(c));
        if (map.get("clusterUser") == null)
        {
            _log.error("cluster user is null in container: " + c.getPath());
        }

        return map;
    }

    public void runTestJob(Container c, User u) throws PipelineJobException
    {
        TestCase.runTestJob(c, u, this);
    }

    abstract protected Pair<String, String> getStatusForJob(ClusterJob job, Container c);

    private File getSerializedJobFile(File jobLogFile)
    {
        if (jobLogFile == null)
        {
            return null;
        }

        String name = FileUtil.getBaseName(jobLogFile.getName());

        return new File(jobLogFile.getParentFile(), name + ".job.json.txt");
    }

    protected File writeJobToFile(PipelineJob job) throws IOException
    {
        //next, serialize job to JSON.  deleting any existing file which might be from a previous task
        File serializedJobFile = getSerializedJobFile(job.getLogFile());
        if (NetworkDrive.exists(serializedJobFile))
        {
            _log.info("job JSON already exists, deleting");
            serializedJobFile.delete();
        }

        job.writeToFile(serializedJobFile);

        return serializedJobFile;
    }

    private final Set<String> INACTIVE_STATUS = new CaseInsensitiveHashSet()
    {{
        add(PipelineJob.TaskStatus.cancelled.name().toUpperCase());
        add(PipelineJob.TaskStatus.error.name().toUpperCase());
        add(PipelineJob.TaskStatus.complete.name().toUpperCase());
    }};

    @NotNull
    private List<ClusterJob> getClusterSubmissionsForJob(String jobId, boolean includeInactive)
    {
        TableInfo ti = ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("jobId"), jobId);
        filter.addCondition(FieldKey.fromString("location"), getConfig().getLocation());
        filter.addCondition(FieldKey.fromString("clusterId"), null, CompareType.NONBLANK);

        if (!includeInactive)
        {
            for (String status : INACTIVE_STATUS)
            {
                filter.addCondition(FieldKey.fromString("status"), status, CompareType.NEQ_OR_NULL);
            }
        }

        filter.addCondition(FieldKey.fromString("status"), PREPARING, CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("status"), NOT_SUBMITTED, CompareType.NEQ_OR_NULL);

        List<ClusterJob> clusterJobs = new TableSelector(ti, filter, new Sort("-created")).getArrayList(ClusterJob.class);
        if (clusterJobs.isEmpty())
        {
            //translate jobId to statusFileId:
            PipelineStatusFile sf = PipelineService.get().getStatusFile(jobId);
            if (sf != null)
            {
                List<ClusterJob> potentialJobs = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("statusFileId"), sf.getRowId()), new Sort("-created")).getArrayList(ClusterJob.class);

                //potentially filter out inactive
                if (!includeInactive)
                {
                    potentialJobs.removeIf(x -> INACTIVE_STATUS.contains(x.getStatus()));
                }

                if (!potentialJobs.isEmpty())
                {
                    //save the new jobID for the most recent job
                    ClusterJob clusterJob = potentialJobs.get(0);
                    if (!clusterJob.getJobId().equalsIgnoreCase(jobId))
                    {
                        _log.error("updating jobId from " + clusterJob.getJobId() + "  to " + jobId + ", status: " + clusterJob.getStatus());
                        clusterJob.setJobId(sf.getJobId());
                        Table.update(null, ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS), clusterJob, clusterJob.getRowId());
                    }

                    clusterJobs = potentialJobs;
                }
            }
        }

        return clusterJobs.isEmpty() ? Collections.emptyList() : clusterJobs;
    }

    protected ClusterJob getClusterSubmission(String clusterId)
    {
        TableInfo ti = ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("clusterId"), clusterId);
        filter.addCondition(FieldKey.fromString("location"), getConfig().getLocation());

        List<ClusterJob> ret = new TableSelector(ti, filter, new Sort("-created")).getArrayList(ClusterJob.class);
        if (ret.size() > 1)
        {
            _log.error("multiple cluster submissions exist for same id: " + clusterId);
        }

        return ret.isEmpty() ? null : ret.get(0);
    }

    public synchronized void requeueBlockedJobs() throws PipelineJobException
    {
        if (!ClusterManager.get().isPreventClusterInteraction())
        {
            //first see if we have any submissions to check
            TableInfo ti = ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("status"), NOT_SUBMITTED, CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("location"), getConfig().getLocation());

            TableSelector ts = new TableSelector(ti, filter, null);
            List<ClusterJob> jobs = ts.getArrayList(ClusterJob.class);
            if (jobs.isEmpty())
            {
                return;
            }

            for (ClusterJob j : jobs)
            {
                PipelineJob job = PipelineJobService.get().getJobStore().getJob(j.getJobId());
                if (job == null)
                {
                    _log.error("unable to find PipelineJob matching: " + j.getJobId(), new Exception());
                }
                else
                {
                    doSubmitJobToCluster(j, job);
                }
            }
        }
    }

    abstract protected Set<String> updateStatusForAllJobs() throws PipelineJobException;

    @Override
    public void updateStatusForJobs(@NotNull Collection<String> jobIds) throws PipelineJobException
    {
        _log.info("updating job status for: " + jobIds.size() + " jobs");
        updateStatusForAll(jobIds);
    }

    public synchronized void updateStatusForAll() throws PipelineJobException
    {
        updateStatusForAll(Collections.emptySet());
    }

    public synchronized void updateStatusForAll(Collection<String> extraJobIds) throws PipelineJobException
    {
        if (ClusterManager.get().isPreventClusterInteraction())
        {
            return;
        }

        Collection<ClusterJob> jobs = getJobsToCheck(true, extraJobIds);
        if (jobs.isEmpty())
        {
            return;
        }

        Set<String> jobsUpdated = updateStatusForAllJobs();

        // iterate existing submissions to catch completed tasks and errors
        // regenerate this list in case status has otherwise changed
        jobs = getJobsToCheck(false, extraJobIds);
        _log.info("found " + jobs.size() + " additional cluster jobs to check");

        for (ClusterJob j : jobs)
        {
            if (jobsUpdated.contains(j.getClusterId()))
            {
                continue;
            }
            else if (j.getClusterId() == null)
            {
                _log.error("clusterId was null for job: " + j.getRowId() + " / " + j.getStatus(), new Exception());
                continue;
            }

            //check condor_history
            Pair<String, String> jobStatus = getStatusForJob(j, ContainerManager.getForId(j.getContainer()));
            if (jobStatus != null)
            {
                _log.info("updating job status: " + j.getClusterId() + " / " + jobStatus.first);
                updateJobStatus(jobStatus.first, j, jobStatus.second);
            }
            else
            {
                _log.error("unable to find record of job submission: " + j.getClusterId());
            }
        }
    }

    private boolean doSubmitJobToCluster(ClusterJob j, PipelineJob job) throws PipelineJobException
    {
        boolean success = false;
        List<String> ret = submitJobToCluster(j, job);
        if (j.getClusterId() != null)
        {
            j.setStatus("SUBMITTED");
            Table.update(job.getUser(), ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS), j, j.getRowId());
            job.setStatus("SUBMITTED");
            job.getLogger().info("Submitted to cluster with jobId: " + j.getClusterId());
            job.getLogger().debug("jobGuid: " + job.getJobGUID());
            job.getLogger().debug("active task: " + job.getActiveTaskId());
            success = true;
        }
        else
        {
            job.getLogger().error("Error submitting job to cluster:");
            job.getLogger().error(StringUtils.join(ret, "\n"));
            job.setActiveTaskStatus(PipelineJob.TaskStatus.error);
        }

        return success;
    }

    protected Collection<ClusterJob> getJobsToCheck(boolean includeConflicting, Collection<String> extraJobIds)
    {
        //first see if we have any submissions to check
        DbSchema schema = ClusterSchema.getInstance().getSchema();
        TableInfo ti = schema.getTable(ClusterSchema.CLUSTER_JOBS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("status"), PageFlowUtil.set(PipelineJob.TaskStatus.error.name().toUpperCase(), PipelineJob.TaskStatus.complete.name().toUpperCase(), PipelineJob.TaskStatus.cancelled.name().toUpperCase(), PREPARING, NOT_SUBMITTED), CompareType.NOT_IN);
        filter.addCondition(FieldKey.fromString("location"), getConfig().getLocation());

        TableSelector ts = new TableSelector(ti, filter, null);
        Set<ClusterJob> jobs = new HashSet<>(ts.getArrayList(ClusterJob.class));

        if (!extraJobIds.isEmpty())
        {
            List<String> filterIds = new ArrayList<>(new HashSet<>(extraJobIds));
            jobs.forEach(x -> filterIds.remove(x.getJobId()));

            if (!filterIds.isEmpty())
            {
                //translate jobId -> statusFile -> clusterId
                Iterator<String> it = filterIds.listIterator();
                while (it.hasNext())
                {
                    String jobId = it.next();

                    // Note: JobId is not always persistent across server restart.
                    // this should account for translating jobId -> statusFileId -> new jobId
                    ClusterJob j = getMostRecentClusterSubmission(jobId, true);
                    if (j != null)
                    {
                        jobs.add(j);
                        it.remove();
                    }
                }
            }

            if (!filterIds.isEmpty())
            {
                _log.error("status update was requested on " + filterIds.size() + " more jobs than expected based on status.  additional IDs: " + StringUtils.join(filterIds, ", "));
                SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString("location"), getConfig().getLocation());
                filter2.addCondition(FieldKey.fromString("jobId"), filterIds, CompareType.IN);
                ts = new TableSelector(ti, filter2, null);
                List<ClusterJob> addedJobs = ts.getArrayList(ClusterJob.class);
                for (ClusterJob j : addedJobs)
                {
                    _log.error("adding additional job: " + j.getJobId() + ", status: " + j.getStatus());
                }

                jobs.addAll(addedJobs);
            }
        }

        //account for records in cluster jobs table where status doesnt match job status
        if (includeConflicting)
        {
            SQLFragment sql = new SQLFragment("SELECT h.* FROM " + ClusterSchema.NAME + "." + ClusterSchema.CLUSTER_JOBS + " h JOIN pipeline.statusfiles p ON (h.jobId = p.entityId) WHERE p.status != h.status");
            List<ClusterJob> conflictingStatus = new SqlSelector(schema, sql).getArrayList(ClusterJob.class);
            if (!conflictingStatus.isEmpty())
            {
                //_log.error("there are " + conflictingStatus.size() + " cluster jobs where the status in the jobs table conflicts with the status in the pipeline table");
                jobs.addAll(conflictingStatus);
            }
        }

        return jobs;
    }

    /**
     * this expects the status normalized from cluster codes to LK TaskStatus
     */
    protected void updateJobStatus(@Nullable String status, ClusterJob j, @Nullable String info) throws PipelineJobException
    {
        //update DB
        boolean statusChanged = (status != null && !status.equals(j.getStatus()));
        j.setLastStatusCheck(new Date());
        j.setStatus(status);

        //no need to redundantly update PipelineJob
        if (!statusChanged)
        {
            Table.update(null, ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS), j, j.getRowId());
            return;
        }

        //and update the actual PipelineJob
        try
        {
            PipelineStatusFile sf = PipelineService.get().getStatusFile(j.getStatusFileId());
            PipelineJob pj = null;
            if (sf != null && status != null)
            {
                File json = getSerializedJobFile(new File(sf.getFilePath()));
                if (!json.exists())
                {
                    throw new PipelineJobException("unable to find pipeline JSON file, expected: " + json.getPath());
                }

                //NOTE: this should read from serialized JSON file, not rely on the DB
                pj = PipelineJob.readFromFile(json);
                if (pj == null)
                {
                    _log.error("unable to create PipelineJob from json file: " + sf.getRowId());
                    return;
                }

                String jobTaskId = pj.getActiveTaskId() == null ? "" : pj.getActiveTaskId().toString();
                if (!jobTaskId.equals(j.getActiveTaskId()))
                {
                    pj.getLogger().debug("pipeline json activeTaskId (" + jobTaskId + ") does not match submission record (" + j.getActiveTaskId() + ").  this probably means it progressed tasks.  will not update status");
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
                    //if the remote job exits w/ a non-zero exit code, cluster might still count this as complete.
                    //to differentiate completed w/ error from successful completion, test activeTaskStatus as recorded in the job json
                    if (taskStatus == PipelineJob.TaskStatus.complete)
                    {
                        if (pj.getActiveTaskStatus() == PipelineJob.TaskStatus.error)
                        {
                            taskStatus = PipelineJob.TaskStatus.error;
                        }
                        else if (pj.getActiveTaskStatus() == PipelineJob.TaskStatus.running)
                        {
                            //this might indicate the job aborted mid-task without properly marking itself as complete
                            pj.getLogger().warn("marking job as complete, even though JSON indicates task status is running.  this might indicate the job aborted improperly?");
                        }
                        else if (pj.getActiveTaskStatus() != PipelineJob.TaskStatus.complete)
                        {
                            //this might indicate the job aborted mid-task without properly marking itself as complete
                            pj.getLogger().warn("Cluster indicates job status is complete, but the job JSON is not marked complete.  this probably indicates the java process aborted improperly.");
                            taskStatus = PipelineJob.TaskStatus.error;
                        }
                        else if (pj.getErrors() > 0)
                        {
                            pj.getLogger().warn("marking job as complete, even though JSON indicates task has errors.  this might indicate the job aborted improperly?");
                        }
                    }

                    pj.getLogger().debug("setting active task status for job: " + j.getClusterId() + " to: " + taskStatus.name() + ". status was: " + pj.getActiveTaskStatus() + " (PipelineJob) /" + sf.getStatus() + " (StatusFile) / activeTaskId (job): " + (pj.getActiveTaskId() != null ? pj.getActiveTaskId().toString() : "no active task") + ", hostname: " + sf.getActiveHostName() + ", rowid: " + j.getRowId());
                    try
                    {
                        //NOTE: PipelineService.setPipelineJobStatus() is needed to requeue a job upon completion, but in all other cases go through JMS
                        if (taskStatus != PipelineJob.TaskStatus.complete)
                        {
                            pj.setStatus(taskStatus, info == null ? sf.getInfo() : info);
                        }
                        else
                        {
                            PipelineService.get().setPipelineJobStatus(pj, taskStatus);
                        }
                    }
                    catch (CancelledException e)
                    {
                        j.setStatus(HTCondorExecutionEngine.StatusType.X.getLabkeyStatus());
                    }
                }
                else
                {
                    _log.warn("unknown TaskStatus: [" + status + "], skipping update");

                    //pj.getLogger().debug("setting status for job: " + j.getClusterId() + " to: " + status + ". status was: " + sf.getStatus() + " (StatusFile) / activeTaskId" + (pj.getActiveTaskId() != null ? pj.getActiveTaskId().toString() : "no active task"));
                    //pj.setStatus(status, sf.getInfo());
                }
            }
            else
            {
                _log.error("unable to find statusfile for job: " + j.getClusterId() + ", status: " + j.getStatus(), new Exception());
                j.setStatus("UNKNOWN");
            }

            Table.update(null, ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS), j, j.getRowId());
            if (pj != null)
            {
                pj.getLogger().debug("updated submission record status to: " + status);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    public void cancelJob(String jobId) throws PipelineJobException
    {
        if (ClusterManager.get().isPreventClusterInteraction())
        {
            _log.info("Cluster interaction disabled, cannot cancel job");
            return;
        }

        //find cluster Id for Job Id
        ClusterJob clusterJob = getMostRecentClusterSubmission(jobId, false);
        if (clusterJob == null)
        {
            //NOTE: if this is the parent of a split job, it was never actually submitted to the cluster
            PipelineStatusFile sf = PipelineService.get().getStatusFile(jobId);
            PipelineJob pj = sf == null ? null : sf.createJobInstance();
            if (pj != null && pj.isSplitWaiting())
            {
                pj.setStatus(PipelineJob.TaskStatus.cancelled);
                return;
            }

            _log.error("unable to find active cluster submission for jobId: " + jobId, new Exception());
            if (sf != null)
            {
                _log.error("status: " + sf.getStatus());
                if  (pj != null)
                {
                    pj.getLogger().error("unable to find active cluster submission for jobId: " + jobId + ", current status: " + sf.getStatus(), new Exception());
                }
            }

            List<ClusterJob> jobs = getClusterSubmissionsForJob(jobId, true);
            if (!jobs.isEmpty())
            {
                _log.error("all submission records: ");
                for (ClusterJob j : jobs)
                {
                    _log.error(j.getJobId() + ", " + j.getStatus() + ", " + j.getClusterId() + ", " + j.getStatusFileId());
                }
            }
            else
            {
                _log.error("no records of job submissions");
            }

            return;
        }

        //this means job was never actually submitted to cluster, so just mark cancelled
        if (NOT_SUBMITTED.equals(clusterJob.getStatus()))
        {
            clusterJob.setStatus(PipelineJob.TaskStatus.cancelling.name().toUpperCase());
            Table.update(null, ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS), clusterJob, clusterJob.getRowId());

            //if this job was never submitted, the job in the database should be up to date
            PipelineStatusFile sf = PipelineService.get().getStatusFile(clusterJob.getStatusFileId());
            if (sf == null)
            {
                sf.createJobInstance().setStatus(PipelineJob.TaskStatus.cancelled);
            }
            else
            {
                _log.error("unable to find rowid for PipelineJob with Id: " + jobId + ", status file: " + clusterJob.getStatusFileId(), new Exception());
            }
        }

        boolean success = removeJob(clusterJob);
        if (success)
        {
            clusterJob.setStatus(PipelineJob.TaskStatus.cancelling.name().toUpperCase());
            Table.update(null, ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS), clusterJob, clusterJob.getRowId());
        }
    }

    abstract protected boolean removeJob(ClusterJob clusterJob);

    private ClusterJob getMostRecentClusterSubmission(String jobId, boolean includeInactive)
    {
        List<ClusterJob> jobs = getClusterSubmissionsForJob(jobId, includeInactive);
        if (jobs.isEmpty())
        {
            return null;
        }

        return jobs.get(0);
    }

    protected List<String> execute(String command)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("attempting to execute a null command");
        }

        if (isDebug())
            _log.info("executing cluster command: " + command);

        List<String> ret = new ArrayList<>();

        try
        {
            Process p = Runtime.getRuntime().exec(command);
            try
            {
                String output = IOUtils.toString(p.getInputStream(), StringUtilsLabKey.DEFAULT_CHARSET);
                String errorOutput = IOUtils.toString(p.getErrorStream(), StringUtilsLabKey.DEFAULT_CHARSET);

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
                _log.error("Error executing cluster command: " + command);
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
            _log.error("Error executing cluster command: " + command);
            _log.info(StringUtils.join(ret, "\n"));
            _log.error(e.getMessage(), e);
        }

        return null;
    }

    protected void checkForCompletedJob(ClusterJob job)
    {
        // TODO: consider also checking the DB for submissions that are marked as complete.
        // this might indicate there was an JMS issue actually changing status on a job
        TableInfo ti = ClusterSchema.getInstance().getSchema().getTable(ClusterSchema.CLUSTER_JOBS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("clusterId"), job.getClusterId());
        filter.addCondition(FieldKey.fromString("status"), PipelineJob.TaskStatus.complete.name().toUpperCase());
        if (new TableSelector(ti, filter, null).exists())
        {
            _log.error("unable to find record of job from condor; however, the submissions table indicates it was marked as complete.  this might indicate a lost JMS message to update the job's status.", new Exception());
            //return PipelineJob.TaskStatus.complete.name().toUpperCase();
        }
    }
}
