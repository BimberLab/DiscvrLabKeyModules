package org.labkey.snprc_scheduler.services;

import org.json.JSONObject;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;
import org.labkey.api.snd.SNDService;
import org.labkey.api.snprc_scheduler.SNPRC_schedulerService;
import org.labkey.api.util.GUID;
import org.labkey.snprc_scheduler.domains.Timeline;
import org.labkey.snprc_scheduler.security.QCStateEnum;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//import org.labkey.api.snd.QCStateEnum;

/**
 * Created by thawkins on 10/21/2018
 */
public class SNPRC_schedulerServiceImpl implements SNPRC_schedulerService
{
    public static final SNPRC_schedulerServiceImpl INSTANCE = new SNPRC_schedulerServiceImpl();

    private SNPRC_schedulerServiceImpl()
    {
    }

    public List<Map<String, Object>> getActiveProjects(Container c, User u, SimpleFilter[] filters) {

       return  SNDService.get().getActiveProjects(c,u, filters);

    }

    /**
     * returns a list of active timelines for a projectId/RevisionNum
     */
    public List<JSONObject> getActiveTimelines(Container c, User u, int projectId, int revisionNum, BatchValidationException errors) throws ApiUsageException
    {


        //TODO: uncomment after datasets are created
//        List<Map<String, Timeline>> timelines = new ArrayList<>();
//        UserSchema schema = SNPRC_schedulerManager.getSNPRC_schedulerUserSchema(c, u);
//        TableInfo timelineTable =  SNPRC_schedulerSchema.getInstance().getTableInfoTimeline();
//        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProjectId"), projectId, CompareType.EQUAL);
//        filter.addCondition(FieldKey.fromParts("RevisionNum"), revNum, CompareType.EQUAL);
//        TableSelector ts = new TableSelector(timelineTable, filter, null);
//
//        List<Timeline> timelines = new TableSelector(table, filter, null).getArrayList(Timeline.class);

//        try (TableResultSet rs = ts.getResultSet())
//        {
//            Timeline timeline = new Timeline();
//
//            for (Map<String, Object> row : rs)
//            {
//
//                //timelines.add();
//            }
//        }
//        catch (SQLException e)
//        {
//        }

            DateFormat formatString = new SimpleDateFormat("MM/dd/yyyy");
            List<JSONObject> timelines = new ArrayList<>();
            //List<Map<String, Timeline>> timelines = new ArrayList<>();
            //List<Timeline> timelines = new ArrayList<>();



        try
        {
            Timeline timeline1 = new Timeline();
            timeline1.setTimelineId(1);
            timeline1.setRevisionNum(0);
            timeline1.setDescription("Timeline #1 Revision 0");
            timeline1.setStartDate(formatString.parse("01/01/2018"));
            timeline1.setEndDate(formatString.parse("05/31/2018"));
            timeline1.setLeadTechs("John Wayne, Clint Eastwood");
            timeline1.setSchedulerNotes("Don't schedule on weekends.");
            timeline1.setNotes("Go ahead, make my day!");
            timeline1.setObjectId(GUID.makeGUID());
            timeline1.setProjectId(projectId);
            timeline1.setProjectRevNum(revisionNum);
            timeline1.setDateCreated(formatString.parse("10/1/2018"));
            timeline1.setDateModified(formatString.parse("10/4/2018"));
            timeline1.setCreatedBy("thawkins");
            timeline1.setModifiedBy("dsmith");
            timeline1.setQcState(QCStateEnum.getValueByName("COmpleted"));
            timeline1.setIsDraft(false);


            timelines.add(timeline1.toJSON(c, u));

            Timeline timeline2 = new Timeline();
            timeline2.setTimelineId(1);
            timeline2.setRevisionNum(1);
            timeline2.setDescription("Timeline #1 Revision 1");
            timeline2.setStartDate(formatString.parse("06/01/2018"));
            timeline2.setEndDate(formatString.parse("12/31/2018"));
            timeline2.setLeadTechs("Zaphod Beeblebrox, Trisha McMillian");
            timeline2.setNotes("Not again.");
            timeline2.setObjectId(GUID.makeGUID());
            timeline2.setProjectId(projectId);
            timeline2.setProjectRevNum(revisionNum);
            timeline2.setDateCreated(formatString.parse("09/20/2018"));
            timeline2.setDateModified(formatString.parse("10/1/2018"));
            timeline2.setCreatedBy("srouse");
            timeline2.setModifiedBy("charlesp");
            timeline2.setSchedulerNotes("The ships hung in the sky in much the same way that bricks don’t.");
            timeline2.setQcState(QCStateEnum.getValueByName("In Progress"));
            timeline2.setIsDraft(false);



            timelines.add(timeline2.toJSON(c, u));


            Timeline timeline3 = new Timeline();
            timeline3.setTimelineId(3);
            timeline3.setRevisionNum(0);
            timeline3.setDescription("Timeline #3");
            timeline3.setStartDate(formatString.parse("02/1/2018"));
            timeline3.setEndDate(formatString.parse("12/30/2018"));
            timeline3.setLeadTechs("Henry Ford, Nicoli Tesla");
            timeline3.setObjectId(GUID.makeGUID());
            timeline3.setProjectId(projectId);
            timeline3.setProjectRevNum(revisionNum);
            timeline3.setDateCreated(formatString.parse("09/20/2018"));
            timeline3.setDateModified(formatString.parse("10/1/2018"));
            timeline3.setCreatedBy("dsmith");
            timeline3.setModifiedBy("charlesp");
            timeline3.setSchedulerNotes("Of all the things i've lost in life, i miss my mind the most");
            timeline3.setIsDraft(false);
            timeline3.setQcState(QCStateEnum.getValueByName("Completed"));



            timelines.add(timeline3.toJSON(c, u));


        }

        catch (Exception e)

        {
            throw new ApiUsageException(e);
        }
        return timelines;
    }
}
