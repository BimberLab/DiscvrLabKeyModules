/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.ogasync;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.JobRunner;
import org.quartz.CronScheduleBuilder;
import org.quartz.DailyTimeIntervalScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.TimeOfDay;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class OGASyncManager
{
    private static final OGASyncManager _instance = new OGASyncManager();

    private OGASyncManager()
    {

    }

    public static OGASyncManager get()
    {
        return _instance;
    }

    private static final Logger _log = Logger.getLogger(OGASyncManager.class);
    public static final String LABKEY_USER_PROP_NAME = "labkeyUser";
    public static final String LABKEY_CONTAINER_PROP_NAME = "labkeyContainer";
    public static final String ENABLED_PROP_NAME = "etlStatus";
    public static final String HOUR_PROP_NAME = "hourOfDay";
    public static final String DATA_SOURCE_PROP_NAME = "dataSourceName";
    public static final String LAST_RUN_PROP_NAME = "lastRun";
    public static final String SCHEMA_PROP_NAME = "schemaName";
    public static final String OGA_QUERY_PROP_NAME = "ogaQueryName";
    public static final String ALL_QUERY_PROP_NAME = "allQueryName";

    public static final String CONFIG_PROPERTY_DOMAIN = "org.labkey.ogasync.etl.config";

    private JobDetail _job = null;
    private Trigger _trigger = null;
    private Integer _hourOfDay = null;

    public synchronized void schedule()
    {
        if (!isEnabled())
            return;

        try
        {
            validateSettings();
            _hourOfDay = getHourOfDay();

            if (_hourOfDay <= 0)
            {
                _log.error("OGA sync has an invalid frequency, will not schedule: " + _hourOfDay);
                return;
            }

            if (_job == null)
            {
                _job = JobBuilder.newJob(OGASyncRunner.class)
                        .withIdentity(OGASyncRunner.class.getCanonicalName(), OGASyncRunner.class.getCanonicalName())
                        .usingJobData("ogaSync", OGASyncRunner.class.getName())
                        .build();
            }

            _trigger = TriggerBuilder.newTrigger()
                    .withIdentity(OGASyncRunner.class.getCanonicalName(), OGASyncRunner.class.getCanonicalName())
                    .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(_hourOfDay, 0))
                    .forJob(_job)
                    .build();

            StdSchedulerFactory.getDefaultScheduler().scheduleJob(_job, _trigger);

            _log.info("OGA sync scheduled to run at " + getHourOfDay() + ":00 each day");
        }
        catch (IllegalArgumentException e)
        {
            _log.error("OGA sync is enabled, but the saved setting are invalid.  Sync will not start.", e);
            return;
        }
        catch (Exception e)
        {
            _log.error("Error scheduling OGA sync", e);
        }
    }

    public synchronized void unschedule()
    {
        if (_job != null)
        {
            try
            {
                StdSchedulerFactory.getDefaultScheduler().deleteJob(_job.getKey());
                _trigger = null;
                _log.info("OGA sync unscheduled");
            }
            catch (Exception e)
            {
                _log.error("Error unscheduling OGA sync", e);
            }
        }
    }

    public void onSettingsChange()
    {
        if (!isEnabled())
        {
            if (_job != null)
                unschedule();
        }
        else
        {
            try
            {
                validateSettings();

                if (_job == null)
                {
                    schedule();
                }
                else
                {
                    if (!_hourOfDay.equals(getHourOfDay()))
                    {
                        _log.info("Rescheduling OGA Sync due to change in settings");
                        unschedule();
                        schedule();
                    }

                    //this indicates it is already scheduled with the correct frequency
                }
            }
            catch (Exception e)
            {
                //ignore
            }
        }
    }

    public void init()
    {
        if (isEnabled() && _trigger == null)
        {
            _log.info("scheduling OGA sync");
            JobRunner.getDefault().execute(new Runnable(){
                public void run()
                {
                    schedule();
                }
            }, 10000);
        }
    }

    public Date getNextRun()
    {
        if (_trigger == null)
            return null;

        return _trigger.getFireTimeAfter(new Date());
    }

    public int getHourOfDay()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(HOUR_PROP_NAME);
        if (prop == null)
            return -1;

        Integer value = Integer.parseInt(prop);
        if (value == null)
        {
            return -1;
        }

        return value;
    }

    public Date getLastRun()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(LAST_RUN_PROP_NAME);
        if (prop == null)
            return null;

        Long value = Long.parseLong(prop);
        if (value == null)
        {
            return null;
        }

        return new Date(value);
    }

    public String getDataSourceName()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(DATA_SOURCE_PROP_NAME);
        if (prop == null)
            return null;

        return prop;
    }

    public String getSchemaName()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(SCHEMA_PROP_NAME);
        if (prop == null)
            return null;

        return prop;
    }


    public String getOgaQueryName()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(OGA_QUERY_PROP_NAME);
        if (prop == null)
            return null;

        return prop;
    }

    public String getAllQueryName()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(ALL_QUERY_PROP_NAME);
        if (prop == null)
            return null;

        return prop;
    }

    public boolean isEnabled()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(ENABLED_PROP_NAME);
        if (prop == null)
            return false;

        Boolean value = Boolean.parseBoolean(prop);
        if (value == null)
        {
            return false;
        }

        return value;
    }

    public User getLabKeyUser()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(LABKEY_USER_PROP_NAME);
        if (prop == null)
            return null;

        try
        {
            ValidEmail email = new ValidEmail(prop);
            return UserManager.getUser(email);
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            _log.error("Invalid email saved for OGA Sync: " + e.getMessage());
        }

        return null;
    }

    public Container getLabKeyContainer()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(LABKEY_CONTAINER_PROP_NAME);
        if (prop == null)
            return null;

        return ContainerManager.getForPath(prop);
    }

    public void validateSettings() throws IllegalArgumentException
    {
        if (getLabKeyUser() == null)
            throw new IllegalArgumentException("Unknown or invalid LabKey User");

        if (PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(LABKEY_CONTAINER_PROP_NAME) == null)
            throw new IllegalArgumentException("No LabKey container set");

        int hour = getHourOfDay();
        if (hour > 23)
            throw new IllegalArgumentException("Hour of day cannot be more than 23");

        if (isEnabled())
        {
            if (hour < 0)
                throw new IllegalArgumentException("Hour of day must be between 0-23");

            if (getDataSourceName() == null)
                throw new IllegalArgumentException("Must provide a data source name");

            DbScope scope = DbScope.getDbScope(getDataSourceName());
            if (scope == null)
                throw new IllegalArgumentException("Unknown data source: " + getDataSourceName());

            if (getSchemaName() == null)
                throw new IllegalArgumentException("Must provide a schemaName");

            if (getOgaQueryName() == null)
                throw new IllegalArgumentException("Must provide a queryName for OGA aliases");

            if (getAllQueryName() == null)
                throw new IllegalArgumentException("Must provide a queryName for all aliases");

            DbSchema schema = scope.getSchema(getSchemaName(), DbSchemaType.Bare);
            if (schema == null)
                throw new IllegalArgumentException("Unknown schema: " + getSchemaName());

            TableInfo ti = schema.getTable(getOgaQueryName());
            if (ti == null)
                throw new IllegalArgumentException("Unknown table: " + getOgaQueryName());

            TableInfo ti2 = schema.getTable(getAllQueryName());
            if (ti2 == null)
                throw new IllegalArgumentException("Unknown table: " + getOgaQueryName());
        }
    }

    public void setLastRun(Date date)
    {
        PropertyManager.PropertyMap pm = PropertyManager.getWritableProperties(CONFIG_PROPERTY_DOMAIN, true);
        pm.put(LAST_RUN_PROP_NAME, String.valueOf(date.getTime()));
        pm.save();
    }
}