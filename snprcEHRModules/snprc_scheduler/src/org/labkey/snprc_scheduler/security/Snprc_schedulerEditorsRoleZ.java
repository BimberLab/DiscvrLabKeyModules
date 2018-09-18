package org.labkey.snprc_scheduler.security;

import org.labkey.api.security.roles.AbstractRole;


/**
 * Created by thawkins on 9/12/2018.
 */
public class Snprc_schedulerEditorsRoleZ extends AbstractRole
{
    public Snprc_schedulerEditorsRoleZ()
    {
        super("SNPRC Schedule editors", "This role is required tp edit SNPRC Timelines and Schedules.",
                Snprc_schedulerEditorsPermissionZ.class
        );


    }
}