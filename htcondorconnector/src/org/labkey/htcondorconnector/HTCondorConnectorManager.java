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

package org.labkey.htcondorconnector;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.htcondorconnector.pipeline.HTCondorExecutionEngine;
import org.quartz.DailyTimeIntervalScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import java.io.File;
import java.util.Map;

public class HTCondorConnectorManager
{
    private static final HTCondorConnectorManager _instance = new HTCondorConnectorManager();
    private static final Logger _log = Logger.getLogger(HTCondorConnectorManager.class);
    private JobDetail _job = null;

    public final static String CONFIG_PROPERTY_DOMAIN = "org.labkey.htcondorconnector.settings";
    public final static String PREVENT_NEW_JOBS = "preventNewJobs";

    private HTCondorConnectorManager()
    {
        // prevent external construction with a private default constructor
    }

    public static HTCondorConnectorManager get()
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
                        .withIdentity(HTCondorExecutionEngine.class.getCanonicalName(), HTCondorExecutionEngine.class.getCanonicalName())
                        .build();
            }

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(HTCondorExecutionEngine.class.getCanonicalName(), HTCondorExecutionEngine.class.getCanonicalName())
                    .withSchedule(DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule().withIntervalInMinutes(1))
                    .forJob(_job)
                    .build();

            StdSchedulerFactory.getDefaultScheduler().scheduleJob(_job, trigger);

            _log.info("HTCondorExecutionEngine scheduled to update jobs every 5 minutes");
        }
        catch (Exception e)
        {
            _log.error("Error scheduling HTCondorExecutionEngine update", e);
        }
    }

    public synchronized void unschedule()
    {
        if (_job != null)
        {
            try
            {
                StdSchedulerFactory.getDefaultScheduler().deleteJob(_job.getKey());
                _log.info("HTCondorExecutionEngine update unscheduled");
            }
            catch (Exception e)
            {
                _log.error("Error unscheduling HTCondorExecutionEngine", e);
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
                _log.warn("HTCondor task update is already running, aborting duplicate check");
                return;
            }

            try
            {
                isRunning = true;

                for (RemoteExecutionEngine e : PipelineJobService.get().getRemoteExecutionEngines())
                {
                    if (e.getType().equals(HTCondorExecutionEngine.TYPE) && e instanceof HTCondorExecutionEngine)
                    {
                        try
                        {
                            ((HTCondorExecutionEngine) e).updateStatusForAll();
                            ((HTCondorExecutionEngine) e).requeueBlockedJobs();
                        }
                        catch (PipelineJobException ex)
                        {
                            throw new JobExecutionException(ex.getMessage(), ex);
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

    public void saveSettings(Map<String, String> props) throws IllegalArgumentException
    {
        PropertyManager.PropertyMap configMap = PropertyManager.getWritableProperties(HTCondorConnectorManager.CONFIG_PROPERTY_DOMAIN, true);

        Boolean preventNewJobs = false;
        String val = StringUtils.trimToNull(props.get(PREVENT_NEW_JOBS));
        if (val != null)
        {
            try
            {
                preventNewJobs = ConvertHelper.convert(val, Boolean.class);
            }
            catch (ConversionException e)
            {
                _log.error("unable to parse value for htcondor preventNewJobs: " + val);
            }
        }

        configMap.put(PREVENT_NEW_JOBS, preventNewJobs.toString());

        configMap.save();
    }

    public boolean isPreventNewJobs()
    {
        PropertyManager.PropertyMap props = PropertyManager.getProperties(HTCondorConnectorManager.CONFIG_PROPERTY_DOMAIN);
        if (props.containsKey(PREVENT_NEW_JOBS) && "true".equals(props.get(PREVENT_NEW_JOBS)))
        {
            return true;
        }

        return false;
    }
}