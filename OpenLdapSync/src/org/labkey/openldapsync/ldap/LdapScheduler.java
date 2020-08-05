package org.labkey.openldapsync.ldap;

import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.quartz.DailyTimeIntervalScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 1/27/13
 * Time: 3:18 PM
 */
public class LdapScheduler
{
    private static final LdapScheduler _instance = new LdapScheduler();
    private static final Logger _log = LogManager.getLogger(LdapScheduler.class);
    private static JobDetail _job = null;
    private Integer _frequency = null;

    private LdapScheduler()
    {

    }

    public static LdapScheduler get()
    {
        return _instance;
    }

    public synchronized void schedule()
    {
        LdapSettings settings = new LdapSettings();

        if (!settings.isEnabled())
            return;

        try
        {
            settings.validateSettings();

            if (_job == null)
            {
                _job = JobBuilder.newJob(LdapSyncRunner.class)
                        .withIdentity(LdapScheduler.class.getCanonicalName(), LdapScheduler.class.getCanonicalName())
                        .usingJobData("ldapSync", LdapScheduler.class.getName())
                        .build();
            }

            if (settings.getFrequency() == null || settings.getFrequency() <= 0)
            {
                _log.info("LDAP sync has an invalid frequency, will not schedule: " + settings.getFrequency());
                return;
            }

            _frequency = settings.getFrequency();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(LdapScheduler.class.getCanonicalName(), LdapScheduler.class.getCanonicalName())
                    .withSchedule(DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule().withIntervalInHours(_frequency))
                    .forJob(_job)
                    .build();

            StdSchedulerFactory.getDefaultScheduler().scheduleJob(_job, trigger);

            _log.info("LDAP sync scheduled to run every " + settings.getFrequency() + " hours");
        }
        catch (LdapException e)
        {
            _log.error("LDAP sync is enabled, but the saved setting are invalid.  Sync will not start.", e);
            return;
        }
        catch (Exception e)
        {
            _log.error("Error scheduling LDAP sync", e);
        }
    }

    public synchronized void unschedule()
    {
        if (_job != null)
        {
            try
            {
                StdSchedulerFactory.getDefaultScheduler().deleteJob(_job.getKey());
                _log.info("LDAP sync unscheduled");
            }
            catch (Exception e)
            {
                _log.error("Error unscheduling LDAP sync", e);
            }
        }
    }

    public void onSettingsChange()
    {
        LdapSettings settings = new LdapSettings();

        if (!settings.isEnabled())
        {
            if (_job != null)
                unschedule();
        }
        else
        {
            try
            {
                settings.validateSettings();

                if (_job == null)
                {
                    schedule();
                }
                else
                {
                    if (!_frequency.equals(settings.getFrequency()))
                    {
                        unschedule();
                        schedule();
                    }

                    //this indicates it is already scheduled with the correct frequency
                }
            }
            catch (LdapException e)
            {
                //ignore
            }
        }
    }
}
