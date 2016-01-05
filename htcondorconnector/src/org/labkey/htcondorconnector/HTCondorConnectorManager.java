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

import org.apache.log4j.Logger;
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

public class HTCondorConnectorManager
{
    private static final HTCondorConnectorManager _instance = new HTCondorConnectorManager();
    private static final Logger _log = Logger.getLogger(HTCondorConnectorManager.class);
    private JobDetail _job = null;

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
        public Runner()
        {

        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException
        {
            for (RemoteExecutionEngine e : PipelineJobService.get().getRemoteExecutionEngines())
            {
                if (e.getType().equals(HTCondorExecutionEngine.TYPE) && e instanceof HTCondorExecutionEngine)
                {
                    try
                    {
                        ((HTCondorExecutionEngine) e).updateStatusForAll();
                    }
                    catch (PipelineJobException ex)
                    {
                        throw new JobExecutionException(ex.getMessage(), ex);
                    }
                }
            }
        }
    }
}