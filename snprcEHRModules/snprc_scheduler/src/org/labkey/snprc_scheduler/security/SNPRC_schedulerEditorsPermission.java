package org.labkey.snprc_scheduler.security;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by thawkins on 9/12/2018.
 */
public class SNPRC_schedulerEditorsPermission extends AbstractPermission
{
    public SNPRC_schedulerEditorsPermission()
    {
        super("SNPRC_schedulerEditorsPermission", "This is the base permission required to edit SNPRC Timelines and Schedules.");
    }

}