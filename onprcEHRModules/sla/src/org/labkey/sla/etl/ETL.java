/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.sla.etl;

import org.apache.log4j.Logger;
import org.labkey.api.data.PropertyManager;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * User: newton
 * Date: Oct 26, 2010
 * Time: 10:07:34 AM
 */
public class ETL
{
    private final static Logger log = Logger.getLogger(ETL.class);
    private static ScheduledExecutorService executor;
    private static ETLRunnable runnable;
    private static boolean isScheduled = false;
    private static ScheduledFuture future = null;
    public static final String ENABLED_PROP_NAME = "etlStatus";

    static public synchronized void init(int delay)
    {
        if (isEnabled())
            start(delay);
    }

    static public synchronized void start(int delay)
    {
        if (!isScheduled)
        {
            executor = (executor == null ? Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    return new Thread(r, "SLA ETL");
                }
            }) : executor);

            try
            {
                runnable = new ETLRunnable();
                int interval = runnable.getRunIntervalInMinutes();
                if (interval != 0)
                {
                    log.info("Scheduling SLA ETL with " + interval + " minute interval and delay: " + delay);
                    future = executor.scheduleWithFixedDelay(runnable, delay, interval, TimeUnit.MINUTES);
                    setEnabled(true);
                    isScheduled = true;
                }
            }
            catch (Exception e)
            {
                log.error("Could not start incremental db sync", e);
            }
        }
        else
        {
            log.info("SLA ETL is already running");
        }
    }

    static public synchronized void stop()
    {
        log.info("Attempting to stop SLA ETL");

        if (isScheduled)
        {
            runnable.shutdown();
            isScheduled = false;
            executor.shutdownNow();

            log.info("ETL has been stopped");
        }
        else
        {
            log.info("ETL is not scheduled, no action needed");
        }
    }

    static public boolean isScheduled()
    {
        return isScheduled;
    }

    static public long nextSync()
    {
        if (future == null)
            return -1;
        else
            return future.getDelay(TimeUnit.SECONDS);
    }

    /**
     * This allows an admin to manually kick off one ETL sync.  It is primarily used for development
     * and not recommended on production servers
     */
    static public void run()
    {
        if (isEnabled())
        {
            if (runnable == null)
            {
                try
                {
                   runnable = new ETLRunnable();
                }
                catch (IOException e)
                {
                    log.error("Error running ETL: " + e.getMessage());
                }
            }

            if (runnable != null)
                runnable.run();

        }
        else
            log.error("ETL is either disabled to inactive.  Will not start");
    }

    public static boolean isEnabled()
    {
        String prop = PropertyManager.getProperties(ETLRunnable.CONFIG_PROPERTY_DOMAIN).get(ENABLED_PROP_NAME);
        if (prop == null)
            return false;

        Boolean value = Boolean.parseBoolean(prop);
        if (value == null)
        {
            return false;
        }

        return value;
    }

    public static boolean isRunning()
    {
        if (runnable == null)
            return false;

        return runnable.isRunning();
    }

    private static void setEnabled(Boolean enabled)
    {
        PropertyManager.PropertyMap pm = PropertyManager.getWritableProperties(ETLRunnable.CONFIG_PROPERTY_DOMAIN, true);
        pm.put(ENABLED_PROP_NAME, enabled.toString());
        pm.save();
    }
}
