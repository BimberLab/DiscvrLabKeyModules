package org.labkey.snprc_scheduler.security;

import org.labkey.api.security.roles.AbstractRole;


/**
 * Created by thawkins on 9/12/2018.
 */
public class SNPRC_schedulerReadersRole extends AbstractRole
{
    public SNPRC_schedulerReadersRole()
    {
        super("SNPRC Schedule readers", "This role is required to read SNPRC Timelines and Schedules.",
                SNPRC_schedulerReadersPermission.class
        );


    }
}