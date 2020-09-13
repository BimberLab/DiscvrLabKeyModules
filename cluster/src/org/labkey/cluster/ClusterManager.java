/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.cluster;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.quartz.DailyTimeIntervalScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

public class ClusterManager
{
    private static final ClusterManager _instance = new ClusterManager();
    private static final Logger _log = LogManager.getLogger(ClusterManager.class);
    private JobDetail _job = null;

    public final static String PREVENT_CLUSTER_INTERACTION = "PreventClusterInteraction";
    public final static String CLUSTER_USER = "ClusterUser";

    private ClusterManager()
    {
        // prevent external construction with a private default constructor
    }

    public static ClusterManager get()
    {
        return _instance;
    }

    public synchronized void schedule()
    {
        try
        {
            if (_job == null)
            {
                _job = JobBuilder.newJob(Runner.class)
                        .withIdentity(ClusterManager.class.getCanonicalName(), ClusterManager.class.getCanonicalName())
                        .build();
            }

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(ClusterManager.class.getCanonicalName(), ClusterManager.class.getCanonicalName())
                    .withSchedule(DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule().withIntervalInMinutes(1))
                    .forJob(_job)
                    .build();

            StdSchedulerFactory.getDefaultScheduler().scheduleJob(_job, trigger);

            _log.info("ClusterManager scheduled to update jobs every 1 minutes");
        }
        catch (Exception e)
        {
            _log.error("Error scheduling ClusterManager update", e);
        }
    }

    public synchronized void unschedule()
    {
        if (_job != null)
        {
            try
            {
                StdSchedulerFactory.getDefaultScheduler().deleteJob(_job.getKey());
                _log.info("ClusterManager update unscheduled");
            }
            catch (Exception e)
            {
                _log.error("Error unscheduling ClusterManager", e);
            }
        }
    }

    public static class Runner implements Job
    {
        private static boolean isRunning = false;

        public Runner()
        {

        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException
        {
            if (isRunning)
            {
                _log.warn("Cluster task update is already running, aborting duplicate check");
                return;
            }

            try
            {
                isRunning = true;

                if (ClusterManager.get().isPreventClusterInteraction())
                {
                    return;
                }

                for (RemoteExecutionEngine e : PipelineJobService.get().getRemoteExecutionEngines())
                {
                    if (e instanceof RemoteClusterEngine)
                    {
                        try
                        {
                            ((RemoteClusterEngine)e).doScheduledUpdate();
                        }
                        catch (Exception ex)
                        {
                            _log.error(ex.getMessage(), ex);
                        }
                    }
                }
            }
            finally
            {
                isRunning = false;
            }
        }
    }

    public boolean isPreventClusterInteraction()
    {
        Module m = ModuleLoader.getInstance().getModule(ClusterModule.NAME);
        ModuleProperty mp = m.getModuleProperties().get(PREVENT_CLUSTER_INTERACTION);
        String val = StringUtils.trimToNull(mp.getValueContainerSpecific(ContainerManager.getRoot()));
        return ("true".equalsIgnoreCase(val));
    }

    public String getClusterUser(Container c)
    {
        Module m = ModuleLoader.getInstance().getModule(ClusterModule.NAME);
        ModuleProperty mp = m.getModuleProperties().get(CLUSTER_USER);

        return StringUtils.trimToNull(mp.getEffectiveValue(c));
    }
}