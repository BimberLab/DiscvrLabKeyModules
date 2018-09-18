package org.labkey.snprc_scheduler.security;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by thawkins on 9/12/2018.
 */
public class SNPRC_schedulerReadersPermission extends AbstractPermission
{
    public SNPRC_schedulerReadersPermission()
    {
        super("SNPRC_schedulerReadersPermission", "This is the base permission required to read data from SNPRC Timelines and Schedules.");
    }

}