package org.labkey.snprc_scheduler.view;

import org.apache.log4j.Logger;
import org.labkey.api.view.JspView;

public class SchedulerWebPart extends JspView
{
    private static final Logger _log = Logger.getLogger(SchedulerWebPart.class);

    public SchedulerWebPart()
    {
        super("/org/labkey/snprc_scheduler/view/schedule.jsp", null);

        setTitle("SNPRC Scheduler");

    }
}
