package org.labkey.GeneticsCore.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.labkey.api.cluster.ClusterService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Job;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * This task is designed to run remotely and will delete orphan working directories on a remote pipeline server
 *
 * Created by bimber on 7/14/2017.
 */
public class ClusterMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    private static final Logger _log = Logger.getLogger(ClusterMaintenanceTask.class);

    public ClusterMaintenanceTask()
    {

    }

    @Override
    public String getDescription()
    {
        return "Cluster Maintenance";
    }

    @Override
    public String getName()
    {
        return "ClusterMaintenance";
    }


    @Override
    public void run(Logger log)
    {
        TableInfo ti = DbSchema.get("pipeline", DbSchemaType.Module).getTable("StatusFiles");
        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("Job"), new SimpleFilter(FieldKey.fromString("Status"), "COMPLETE", CompareType.NEQ_OR_NULL), null);
        Set<String> jobGuids = new HashSet<>(ts.getArrayList(String.class));

        TableSelector ts2 = new TableSelector(ti, PageFlowUtil.set("EntityId"), new SimpleFilter(FieldKey.fromString("Status"), "COMPLETE", CompareType.NEQ_OR_NULL), null);
        jobGuids.addAll(ts2.getArrayList(String.class));

        JobRunner jr = JobRunner.getDefault();
        for (RemoteExecutionEngine engine : PipelineJobService.get().getRemoteExecutionEngines())
        {
            log.info("Starting maintenance task for: " + engine.getType());

            try
            {
                RemoteWorkTask task = new RemoteWorkTask(jobGuids);
                PipeRoot pr = PipelineService.get().getPipelineRootSetting(ContainerManager.getHomeContainer());
                File subdir = new File(pr.getRootPath(), "clusterMaintenance");
                if (!subdir.exists())
                {
                    subdir.mkdirs();
                }

                File logFile = new File(subdir, "Maintenance-" + engine.getType() + "." + FileUtil.getTimestamp() + ".log");

                jr.execute(new Job()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            User u = new LimitedUser(UserManager.getGuestUser(), new int[0], Collections.singleton(RoleManager.getRole(EditorRole.class)), true);
                            PipelineJob job = ClusterService.get().createClusterRemotePipelineJob(ContainerManager.getHomeContainer(), u, "Maintenance: " + engine.getType(), engine, task, logFile);
                            PipelineService.get().queueJob(job);
                        }
                        catch (PipelineValidationException e)
                        {
                            _log.error(e);
                        }
                    }
                });
            }
            catch (Exception e)
            {
                log.error(e);
            }
        }

        jr.waitForCompletion();
    }

    @Override
    public boolean isEnabledByDefault()
    {
        return false;
    }

    public static class RemoteWorkTask implements ClusterService.ClusterRemoteTask
    {
        private Set<String> _jobGuids;

        public RemoteWorkTask(Set<String> jobGuids)
        {
            _jobGuids = new CaseInsensitiveHashSet(jobGuids);
        }

        @Override
        public void run(Logger log)
        {
            //TODO: inspect WorkDirFactory for base path?
            //WorkDirFactory wdf = PipelineJobService.get().getWorkDirFactory();

            //hacky, but this is only planned to be used by us
            File workDirBase = new File("/home/exacloud/lustre1/prime-seq/workDir/");
            if (!workDirBase.exists())
            {
                log.error("Unable to find workdir: " + workDirBase.getPath());
                return;
            }

            log.info("total active pipeline jobs: " + _jobGuids.size());

            File[] subdirs = workDirBase.listFiles();
            log.info("total work directories found: " + subdirs.length);
            for (File child : subdirs)
            {
                if (child.isDirectory())
                {
                    if (!_jobGuids.contains(child.getName()))
                    {
                        log.info("inspecting directory: " + child.getName());
                        Collection<Path> modifiedRecently = new HashSet<>();
                        final long minDate = DateUtils.addDays(new Date(), -2).getTime();
                        try (DirectoryStream<Path> ds = Files.newDirectoryStream(child.toPath(),x -> x.toFile().lastModified() >= minDate))
                        {
                            ds.forEach(x -> modifiedRecently.add(x));
                        }
                        catch (IOException e)
                        {
                            _log.error(e);
                            continue;
                        }

                        if (modifiedRecently.isEmpty())
                        {
                            log.info("deleting directory: " + child.getName());
                            deleteDirectory(child, log);
                        }
                        else
                        {
                            log.info("directory has " + modifiedRecently.size() + " files modified in the last 48H, skipping for now: " + child.getName());
                            for (Path f : modifiedRecently)
                            {
                                log.debug(f.toFile().getPath());
                            }
                        }
                    }
                }
            }
        }

        private void deleteDirectory(File child, Logger log)
        {
            try
            {
                FileUtils.deleteDirectory(child);
            }
            catch (IOException e)
            {
                log.error(e);
            }
        }
    }
}
