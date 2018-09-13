package org.labkey.snprc_scheduler.security;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by thawkins on 9/12/2018.
 */
public class snprc_schedulerEditorsPermission extends AbstractPermission
{
    public snprc_schedulerEditorsPermission()
    {
        super("snprc_schedulerEditorsPermission", "This is the base permission required to edit SNPRC Timelines and Schedules.");
    }

}