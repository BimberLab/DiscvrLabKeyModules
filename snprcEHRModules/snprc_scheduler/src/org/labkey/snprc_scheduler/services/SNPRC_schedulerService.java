package org.labkey.snprc_scheduler.services;


import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.util.List;

/**
 * Created by thawikins on 10/21/2018
 */

public interface SNPRC_schedulerService
{
    @Nullable
    static SNPRC_schedulerService get()
    {
        return ServiceRegistry.get(SNPRC_schedulerService.class);
    }

    List<JSONObject> getActiveTimelines(Container c, User u, int projectId, int revisionNum, BatchValidationException errors);

}

