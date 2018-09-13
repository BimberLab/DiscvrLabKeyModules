package org.labkey.snprc_scheduler.security;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by thawkins on 9/12/2018.
 */
public class snprc_schedulerReadersPermission extends AbstractPermission
{
    public snprc_schedulerReadersPermission()
    {
        super("snprc_schedulerReadersPermission", "This is the base permission required to read data from SNPRC Timelines and Schedules.");
    }

}