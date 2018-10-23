package org.labkey.snprc_scheduler.services;

import org.labkey.api.data.Container;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.snd.Project;
import org.labkey.api.snd.SNDService;
import org.labkey.snprc_scheduler.domains.Timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.labkey.snprc_scheduler.SNPRC_schedulerManager.getSNPRC_schedulerUserSchema;

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
    public List<Map<String, Timeline>> getActiveTimelines(Container c, User u, int projectId, int revNum, BatchValidationException errors)
    {


            UserSchema schema = getSNPRC_schedulerUserSchema(c, u);

            //TableInfo projectsTable = getTableInfo(schema, SNDSchema.PROJECTS_TABLE_NAME);

            // Get from projects table
//            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProjectId"), projectId, CompareType.EQUAL);
//            filter.addCondition(FieldKey.fromParts("RevisionNum"), revNum, CompareType.EQUAL);
//            TableSelector ts = new TableSelector(projectsTable, filter, null);

            // Unique constraint enforces only one project for projectId/revisionNum
            SNDService sndService = SNDService.get();
            Project project = sndService.getProject(c, u, projectId, revNum);

        //TODO: uncomment after datasets are created
        List<Map<String, Timeline>> timelines = new ArrayList<>();

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
