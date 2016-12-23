package org.labkey.extscheduler;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.Map;

public class ExtSchedulerManager
{
    private static ExtSchedulerManager _instance = new ExtSchedulerManager();

    private ExtSchedulerManager()
    {
    }

    public static ExtSchedulerManager getInstance()
    {
        return _instance;
    }

    public boolean isEventOwner(User user, Map<String, Object> row)
    {
        Integer rowUserId = row.get("UserId") != null ? Integer.parseInt(row.get("UserId").toString()) : null;
        return rowUserId != null && user.getUserId() == rowUserId.intValue();
    }

    public boolean isEventCreator(User user, Map<String, Object> row)
    {
        Integer rowCreatedBy = row.get("CreatedBy") != null ? Integer.parseInt(row.get("CreatedBy").toString()) : null;
        return rowCreatedBy != null && user.getUserId() == rowCreatedBy.intValue();
    }

    public boolean isEventInPast(Map<String, Object> row)
    {
        Date rowStartDate = (Date) row.get("StartDate");
        Date now = new Date();
        return rowStartDate.getTime() < now.getTime();
    }

    private ModuleProperty getModuleProperty(String propName)
    {
        Module slaModule = ModuleLoader.getInstance().getModule(ExtSchedulerModule.class);
        return slaModule.getModuleProperties().get(propName);
    }

    public String getExtSchedulerUserGroupName(Container c)
    {
        ModuleProperty groupName = getModuleProperty("ExtSchedulerUserGroupName");
        if (groupName != null)
            return groupName.getEffectiveValue(c);

        return null;
    }
}
