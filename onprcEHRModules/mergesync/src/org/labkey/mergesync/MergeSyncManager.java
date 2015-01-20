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

package org.labkey.mergesync;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.JobRunner;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Calendar;
import java.util.Date;

public class MergeSyncManager
{
    private static final MergeSyncManager _instance = new MergeSyncManager();
    private JobDetail _job = null;
    private Trigger _trigger = null;

    public static final String TABLE_TESTNAMEMAPPING = "testnamemapping";
    public static final String TABLE_MERGE_TO_LK_MAPPING = "mergetolkmapping";
    public static final String TABLE_ORDERSSYNCED = "orderssynced";

    public static final String TABLE_MERGE_ORDERS = "orders";
    public static final String TABLE_MERGE_RESULTS = "results";
    public static final String TABLE_MERGE_TEST = "tests";
    public static final String TABLE_MERGE_TESTINFO = "testinfo";
    public static final String TABLE_MERGE_CONTAINERS = "containers";
    public static final String TABLE_MERGE_VISITS = "visits";
    public static final String TABLE_MERGE_COPY_TO = "copyto";
    public static final String TABLE_MERGE_VISIT_INS = "vis_ins";
    public static final String TABLE_MERGE_INSURANCE = "insurance";
    public static final String TABLE_MERGE_PERSONNEL = "prsnl";
    public static final String TABLE_MERGE_PATIENTS = "patients";

    private MergeSyncManager()
    {

    }

    public static MergeSyncManager get()
    {
        return _instance;
    }

    private static final Logger _log = Logger.getLogger(MergeSyncManager.class);
    private Integer _syncInterval;

    public static final String DATA_SOURCE_PROP_NAME = "dataSourceName";
    public static final String SCHEMA_PROP_NAME = "schemaName";

    public static final String LABKEY_USER_PROP_NAME = "labkeyUser";
    public static final String LABKEY_CONTAINER_PROP_NAME = "labkeyContainer";
    public static final String SYNC_INTERVAL_PROP_NAME = "syncInterval";

    public static final String PULL_ENABLED_PROP_NAME = "pullEnabled";
    public static final String PUSH_ENABLED_PROP_NAME = "pushEnabled";
    public static final String SYNC_ANIMALS_PROP_NAME = "syncAnimalsAndProjects";
    public static final String LAST_RUN_PROP_NAME = "lastRun";
    public static final String MERGE_USER_PROP_NAME = "mergeUserName";

    public static final String CONFIG_PROPERTY_DOMAIN = "org.labkey.mergesync.etl.config";

    public synchronized void schedule()
    {
        if (!isEnabled())
            return;

        try
        {
            validateSettings();
            _syncInterval = getSyncInterval();

            if (_syncInterval <= 0)
            {
                _log.error("Merge sync has an invalid frequency, will not schedule: " + _syncInterval);
                return;
            }

            if (_job == null)
            {
                _job = JobBuilder.newJob(MergeSyncRunner.class)
                        .withIdentity(MergeSyncRunner.class.getCanonicalName(), MergeSyncRunner.class.getCanonicalName())
                        .usingJobData("mergeSync", MergeSyncRunner.class.getName())
                        .build();
            }

            _trigger = TriggerBuilder.newTrigger()
                    .withIdentity(MergeSyncRunner.class.getCanonicalName(), MergeSyncRunner.class.getCanonicalName())
                    .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(_syncInterval))
                    .forJob(_job)
                    .build();

            StdSchedulerFactory.getDefaultScheduler().scheduleJob(_job, _trigger);

            _log.info("Merge sync scheduled to run every " + getSyncInterval() + " minutes");
        }
        catch (IllegalArgumentException e)
        {
            _log.error("Merge sync is enabled, but the saved setting are invalid.  Sync will not start.", e);
            return;
        }
        catch (Exception e)
        {
            _log.error("Error scheduling Merge sync", e);
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
                _log.info("Merge sync unscheduled");
            }
            catch (Exception e)
            {
                _log.error("Error unscheduling Merge sync", e);
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
                    if (!_syncInterval.equals(getSyncInterval()))
                    {
                        _log.info("Rescheduling Merge Sync due to change in settings");
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

    public Date getNextRun()
    {
        if (_trigger == null)
            return null;

        return _trigger.getFireTimeAfter(new Date());
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

    public void setLastRun(Date date)
    {
        PropertyManager.PropertyMap pm = PropertyManager.getWritableProperties(CONFIG_PROPERTY_DOMAIN, true);
        date = DateUtils.truncate(date, Calendar.MINUTE);  //always round to nearest minute
        pm.put(LAST_RUN_PROP_NAME, String.valueOf(date.getTime()));
        pm.save();
    }

    public void init()
    {
        if (isEnabled() && _trigger == null)
        {
            _log.info("scheduling Merge sync");
            JobRunner.getDefault().execute(new Runnable(){
                public void run()
                {
                    schedule();
                }
            }, 60000);
        }
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
            _log.error("Invalid email saved for Merge Sync: " + e.getMessage());
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

    public int getSyncInterval()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(SYNC_INTERVAL_PROP_NAME);
        if (prop == null)
            return -1;

        Integer value = Integer.parseInt(prop);
        if (value == null)
        {
            return -1;
        }

        return value;
    }

    public boolean isPullEnabled()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(PULL_ENABLED_PROP_NAME);
        if (prop == null)
            return false;

        Boolean value = Boolean.parseBoolean(prop);
        if (value == null)
        {
            return false;
        }

        return value;
    }

    public boolean doSyncAnimalsAndProjects()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(SYNC_ANIMALS_PROP_NAME);
        if (prop == null)
            return false;

        Boolean value = Boolean.parseBoolean(prop);
        if (value == null)
        {
            return false;
        }

        return value;
    }

    public boolean isPushEnabled()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(PUSH_ENABLED_PROP_NAME);
        if (prop == null)
            return false;

        Boolean value = Boolean.parseBoolean(prop);
        if (value == null)
        {
            return false;
        }

        return value;
    }

    public String getMergeUserName()
    {
        return PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(MERGE_USER_PROP_NAME);
    }

    private boolean isEnabled()
    {
        return isPullEnabled() || isPullEnabled();
    }

    public void validateSettings() throws IllegalArgumentException
    {
        if (isEnabled())
        {
            if (getDataSourceName() == null)
                throw new IllegalArgumentException("Must provide a data source name");

            DbScope scope = DbScope.getDbScope(getDataSourceName());
            if (scope == null)
                throw new IllegalArgumentException("Unknown data source: " + getDataSourceName());

            DbSchema schema = scope.getSchema(getSchemaName(), DbSchemaType.Bare);
            if (schema == null)
                throw new IllegalArgumentException("Unknown schema: " + getSchemaName());
        }

        if (isPullEnabled())
        {
            if (getLabKeyUser() == null)
                throw new IllegalArgumentException("Unknown or invalid LabKey User");

            if (PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(LABKEY_CONTAINER_PROP_NAME) == null)
                throw new IllegalArgumentException("No LabKey container set");
        }

        if (isPushEnabled() || doSyncAnimalsAndProjects())
        {
            if (getMergeUserName() == null)
                throw new IllegalArgumentException("Must provide the name of the merge user to use for sync");
        }
    }

    public DbSchema getMergeSchema()
    {
        DbScope scope = DbScope.getDbScope(MergeSyncManager.get().getDataSourceName());
        if (scope == null)
            return null;

        return scope.getSchema(MergeSyncManager.get().getSchemaName(), DbSchemaType.Bare);
    }
}