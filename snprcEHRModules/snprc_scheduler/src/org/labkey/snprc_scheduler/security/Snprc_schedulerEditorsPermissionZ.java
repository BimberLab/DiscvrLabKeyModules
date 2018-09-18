package org.labkey.snprc_scheduler.security;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by thawkins on 9/12/2018.
 */
public class Snprc_schedulerEditorsPermissionZ extends AbstractPermission
{
    public Snprc_schedulerEditorsPermissionZ()
    {
        super("Snprc_schedulerEditorsPermissionZ", "This is the base permission required to edit SNPRC Timelines and Schedules.");
    }

}