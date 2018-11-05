package org.labkey.snprc_scheduler.domains;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
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
    private Integer _revisionNum;
    private String _description;
    private Date _startDate;
    private Date _endDate;
    private String _leadTechs; // comma separated list of technicians
    private String _notes;
    private String _schedulerNotes;
    private String _objectId;
    private Integer _projectId;
    private Integer _projectRevisionNum;
    private Date _dateCreated;
    private Date _dateModified;
    private String _createdBy;
    private String _modifiedBy;
    private Integer _qcState;
    private List<TimelineItem> _timelineItems = new ArrayList<>(); // list of TimelineItem objects associated with the timeline

    public static final String TIMELINE_ID = "TimelineId";
    public static final String TIMELINE_REVISION_NUM = "RevisionNum";
    public static final String TIMELINE_DESCRIPTION = "Description";
    public static final String TIMELINE_STARTDATE = "StartDate";
    public static final String TIMELINE_ENDDATE = "EndDate";
    public static final String TIMELINE_OBJECTID = "ObjectId";
    public static final String TIMELINE_CONTAINER = "Container";
    public static final String TIMELINE_LEAD_TECHS = "LeadTechs";
    public static final String TIMELINE_NOTES = "Notes";
    public static final String TIMELINE_SCHEDULER_NOTES = "SchedulerNotes";
    public static final String TIMELINE_PROJECT_ID = "ProjectId";
    public static final String TIMELINE_PROJECT_REVISION_NUM = "ProjectRevisionNum";
    public static final String TIMELINE_DATE_CREATED = "DateCreated";
    public static final String TIMELINE_DATE_MODIFIED = "DateModified";
    public static final String TIMELINE_CREATED_BY = "CreatedBy";
    public static final String TIMELINE_MODIFIED_BY = "ModifiedBy";
    public static final String TIMELINE_QCSTATE = "qcState";
    public static final String TIMELINE_TIMELINE_ITEMS = "TimelineItems";

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

    public Integer getProjectId()
    {
        return _projectId;
    }

    public void setProjectId(Integer projectId)
    {
        _projectId = projectId;
    }

    public Integer getRevisionNum()
    {
        return _revisionNum;
    }

    public void setRevisionNum(Integer revisionNum)
    {
        _revisionNum = revisionNum;
    }

    public String getLeadTechs()
    {
        return _leadTechs;
    }

    public void setLeadTechs(String leadTechs)
    {
        _leadTechs = leadTechs;
    }

    public Date getDateCreated()
    {
        return _dateCreated;
    }

    public void setDateCreated(Date dateCreated)
    {
        _dateCreated = dateCreated;
    }

    public Date getDateModified()
    {
        return _dateModified;
    }

    public void setDateModified(Date dateModified)
    {
        _dateModified = dateModified;
    }

    public String getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(String createdBy)
    {
        _createdBy = createdBy;
    }

    public String getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(String modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public String getSchedulerNotes()
    {
        return _schedulerNotes;
    }

    public void setSchedulerNotes(String schedulerNotes)
    {
        _schedulerNotes = schedulerNotes;
    }

    public Integer getProjectRevisionNum()
    {
        return _projectRevisionNum;
    }

    public void setProjectRevisionNum(Integer projectRevisionNum)
    {
        _projectRevisionNum = projectRevisionNum;
    }

    public Integer getQcState()
    {
        return _qcState;
    }

    public void setQcState(Integer qcState)
    {
        _qcState = qcState;
    }

    public String getNotes()
    {
        return _notes;
    }

    public void setNotes(String notes)
    {
        _notes = notes;
    }

    public List<TimelineItem> getTimelineItems()
    {
        return _timelineItems;
    }

    public void setTimelineItems(List<TimelineItem> timelineItems)
    {
        _timelineItems = timelineItems;
    }

    @NotNull
    public Map<String, Object> toMap(Container c)
    {
        Map<String, Object> timelineValues = new ArrayListMap<>();
        timelineValues.put(TIMELINE_ID, getTimelineId());
        timelineValues.put(TIMELINE_REVISION_NUM, getRevisionNum());
        timelineValues.put(TIMELINE_DESCRIPTION, getDescription());
        timelineValues.put(TIMELINE_STARTDATE, getStartDate());
        timelineValues.put(TIMELINE_ENDDATE, getEndDate());
        timelineValues.put(TIMELINE_OBJECTID, getObjectId());
        timelineValues.put(TIMELINE_CONTAINER, c.getId());
        timelineValues.put(TIMELINE_LEAD_TECHS, getLeadTechs());
        timelineValues.put(TIMELINE_NOTES, getNotes());
        timelineValues.put(TIMELINE_SCHEDULER_NOTES,getSchedulerNotes());
        timelineValues.put(TIMELINE_PROJECT_ID, getProjectId());
        timelineValues.put(TIMELINE_PROJECT_REVISION_NUM, getProjectRevisionNum());
        timelineValues.put(TIMELINE_DATE_CREATED, getDateCreated());
        timelineValues.put(TIMELINE_DATE_MODIFIED, getDateModified());
        timelineValues.put(TIMELINE_CREATED_BY, getCreatedBy());
        timelineValues.put(TIMELINE_MODIFIED_BY, getModifiedBy());
        timelineValues.put(TIMELINE_QCSTATE, getQcState());

        return timelineValues;
    }

    @NotNull
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = new JSONObject();


        if (getTimelineId() != null)
            json.put(TIMELINE_ID, getTimelineId());
        if (getRevisionNum() != null)
            json.put(TIMELINE_REVISION_NUM, getRevisionNum());
        json.put(TIMELINE_DESCRIPTION, getDescription());
        json.put(TIMELINE_STARTDATE, getStartDate());
        if (getEndDate() != null)
            json.put(TIMELINE_ENDDATE, getEndDate());
        if (getObjectId() != null)
          json.put(TIMELINE_OBJECTID, getObjectId());

        json.put(TIMELINE_CONTAINER, c.getId());
        json.put(TIMELINE_LEAD_TECHS, getLeadTechs());
        json.put(TIMELINE_NOTES, getNotes());
        json.put(TIMELINE_SCHEDULER_NOTES,getSchedulerNotes());
        json.put(TIMELINE_PROJECT_ID, getProjectId());
        json.put(TIMELINE_PROJECT_REVISION_NUM, getProjectRevisionNum());
        json.put(TIMELINE_DATE_CREATED, getDateCreated());
        json.put(TIMELINE_DATE_MODIFIED, getDateModified());
        json.put(TIMELINE_CREATED_BY, getCreatedBy());
        json.put(TIMELINE_MODIFIED_BY, getModifiedBy());
        json.put(TIMELINE_QCSTATE, getQcState());

        if (getTimelineItems().size() > 0)
        {
            JSONArray jsonTimelineItems = new JSONArray();
            for (TimelineItem timelineItem : getTimelineItems())
            {
                jsonTimelineItems.put(timelineItem.toJSON(c));
            }
            json.put(TIMELINE_TIMELINE_ITEMS, jsonTimelineItems);
        }


        return json;
    }

    @NotNull
    public List<Map<String, Object>> getTimelineItemList(Container c)
    {
        List<Map<String, Object>> list = new ArrayList<>();

        for (TimelineItem timelineItem : getTimelineItems())
        {
            list.add(timelineItem.toMap(c));
        }

        return list;
    }


}