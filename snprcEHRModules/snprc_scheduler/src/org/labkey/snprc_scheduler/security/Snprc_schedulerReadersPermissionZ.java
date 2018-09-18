package org.labkey.snprc_scheduler.security;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by thawkins on 9/12/2018.
 */
public class Snprc_schedulerReadersPermissionZ extends AbstractPermission
{
    public Snprc_schedulerReadersPermissionZ()
    {
        super("Snprc_schedulerReadersPermissionZ", "This is the base permission required to read data from SNPRC Timelines and Schedules.");
    }

}