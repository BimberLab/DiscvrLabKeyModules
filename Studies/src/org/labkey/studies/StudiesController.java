package org.labkey.studies;

import org.labkey.api.action.SpringActionController;

public class StudiesController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(StudiesController.class);
    public static final String NAME = "studies";

    public StudiesController()
    {
        setActionResolver(_actionResolver);
    }
}
