package org.labkey.snprc_scheduler.domains;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;
import org.labkey.api.snd.Project;
import org.labkey.api.util.DateUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
/**
 * Created by thawkins on 9/13/2018.
 * <p>
 * Class for Timeline table data. Used when saving, updating, deleting and getting a Timeline table
 */

public class Timeline
{
    private Integer _timelineId;
    private String _projectObjectId;
    private String _description;
    private Date _startDate;
    private Date _endDate;
    private String _leadTechs; // comma separated list of technicians
    private String _objectId;
    private List<TimelineItem> _timelineItems = new ArrayList<>();
    private Project _project;

    public static final String TIMELINE_ID = "TimelineId";
    public static final String TIMELINE_PROJECT_OBJECT_ID = "ProjectObjectId";
    public static final String TIMELINE_DESCRIPTION = "Description";
    public static final String TIMELINE_STARTDATE = "StartDate";
    public static final String TIMELINE_ENDDATE = "EndDate";
    public static final String TIMELINE_OBJECTID = "ObjectId";
    public static final String TIMELINE_CONTAINER = "Container";


    public Timeline(@Nullable Integer timelineId, @NotNull String projectObjectId, String description, Date startDate, @Nullable Date endDate)
    {
        _timelineId = timelineId;
        _projectObjectId = projectObjectId;
        _description = description;
        _startDate = startDate;
        _endDate = endDate;
    }

    public Timeline()
    {
    }

    public Integer getTimelineId()
    {
        return _timelineId;
    }

    public void setTimelineId(Integer timelineId)
    {
        _timelineId = timelineId;
    }

    public String getProjectObjectId()
    {
        return _projectObjectId;
    }

    public void setProjectObjectId(String projectObjectId)
    {
        _projectObjectId = projectObjectId;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public Date getStartDate()
    {
        return _startDate;
    }

    @Nullable
    public String startDateToString()
    {

        return DateUtil.formatDateISO8601(getStartDate());
    }

    public void setStartDate(Date startDate)
    {
        _startDate = startDate;
    }

    public Date getEndDate()
    {
        return _endDate;
    }

    @Nullable
    public String endDateToString()
    {

        return DateUtil.formatDateISO8601(getEndDate());
    }

    public void setEndDate(Date endDate)
    {
        _endDate = endDate;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }


    public Project getProject()
    {
        return _project;
    }

    public void setProject(Project project)
    {
        _project = project;
    }

    @NotNull
    public Map<String, Object> getTimelineRow(Container c)
    {
        Map<String, Object> timelineValues = new ArrayListMap<>();
        timelineValues.put(TIMELINE_ID, getTimelineId());
        timelineValues.put(TIMELINE_PROJECT_OBJECT_ID, getProjectObjectId());
        timelineValues.put(TIMELINE_DESCRIPTION, getDescription());
        timelineValues.put(TIMELINE_STARTDATE, getStartDate());
        timelineValues.put(TIMELINE_ENDDATE, getEndDate());
        timelineValues.put(TIMELINE_OBJECTID, getObjectId());
        timelineValues.put(TIMELINE_CONTAINER, c.getId());


        if (TIMELINE_PROJECT_OBJECT_ID != null)
        {
            Map<String, Object> project = getProject().getProjectRow(c);
            for (String key : project.keySet())
            {
                timelineValues.put(key, project.get(key));
            }
        }
        return timelineValues;
    }

    @NotNull
    public JSONObject toJSON(Container c)
    {
        JSONObject json = new JSONObject();
        json.put(TIMELINE_ID, getTimelineId());
        json.put(TIMELINE_PROJECT_OBJECT_ID, getProjectObjectId());
        json.put(TIMELINE_DESCRIPTION, getDescription());
        json.put(TIMELINE_STARTDATE, getStartDate());
        json.put(TIMELINE_CONTAINER, c.getId());
        if (getTimelineId() != null) {
            json.put(TIMELINE_ID, getTimelineId());
        }
        if (getEndDate() != null) {
            json.put(TIMELINE_ENDDATE, getEndDate());
        }
        if (getObjectId() != null)
        {
            json.put(TIMELINE_OBJECTID, getObjectId());
        }
        // add timeline items to json object

//        if (getTimelineItems(getTimelineId()).size() > 0)
//        {
//            JSONArray jsonTimelineItems = new JSONArray();
//            for (TimelineItem tiemlineItem : getTimelineItems())
//            {
//                jsonTimelineItems.put(timelineItem.toJSON(c, u));
//            }
//            json.put(TIMELINE_ITEMS, jsonTimelineItems);
//        }


        return json;
    }

}
