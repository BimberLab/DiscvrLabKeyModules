package org.labkey.snprc_scheduler.security;

import org.labkey.api.security.roles.AbstractRole;


/**
 * Created by thawkins on 9/12/2018.
 */
public class Snprc_schedulerReadersRoleZ extends AbstractRole
{
    public Snprc_schedulerReadersRoleZ()
    {
        super("SNPRC Schedule readers", "This role is required to read SNPRC Timelines and Schedules.",
                Snprc_schedulerReadersPermissionZ.class
        );


    }
}