package org.labkey.api.snprc_scheduler;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.util.List;
import java.util.Map;

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

    List<Map<String, Object>> getActiveTimelines(Container c, User u);

}

