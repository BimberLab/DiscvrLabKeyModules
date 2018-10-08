package org.labkey.snprc_scheduler;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.snprc_scheduler.SNPRC_schedulerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by thawkins on 10/21/2018
 */
public class SNPRC_schedulerServiceImpl implements SNPRC_schedulerService
{
    public static final SNPRC_schedulerServiceImpl INSTANCE = new SNPRC_schedulerServiceImpl();

    private SNPRC_schedulerServiceImpl()
    {
    }

    /**
     * returns a list of active timelines
     */
    public List<Map<String, Object>> getActiveTimelines(Container c, User u)
    {
        //TODO: uncomment after datasets are created
        List<Map<String, Object>> timelines = new ArrayList<>();

        /*UserSchema schema = SNPRC_schedulerManager.getSNPRC_schedulerUserSchema(c, u);

        SQLFragment sql = new SQLFragment("SELECT timelineId FROM ");
        sql.append(schema.getTable(SNPRC_schedulerSchema.TABLE_NAME_TIMELINE), "t");
        sql.append(" WHERE t.HasItems = 'true'");
        SqlSelector selector = new SqlSelector(schema.getDbSchema(), sql);

        try (TableResultSet rs = selector.getResultSet())
        {
            for (Map<String, Object> row : rs)
            {
                timelines.add(row);
            }
        }
        catch (SQLException e)
        {
        }*/

        return timelines;
    }

}
