package org.labkey.snprc_scheduler.domains;


import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;

import java.util.Date;
import java.util.Map;

/**
 * Created by thawkins on 9/13/2018.
 * <p>
 * Class for TimelineTable item data. Used in TimelineTable class.
 */
public class TimelineItem
{
    private Integer _timelineItemId;    //PK
    private Integer _timelineId;        // FK - timeline
    private Integer _revisionNum;       // FK - timeline
    private Integer _projectItemId;     // FK - timelineProjectItem
    private Integer _studyDay;
    private Integer _scheduledDay;
    private Integer _qcState;
    private Date _dateCreated;
    private Date _dateModified;
    private String _createdBy;
    private String _modifiedBy;
    private String _objectId;


    public static final String TIMELINEITEM_TIMELINE_ITEM_ID = "TimelineItemId";
    public static final String TIMELINEITEM_TIMELINE_ID = "TimelineId";
    public static final String TIMELINEITEM_REVISION_NUM = "RevisionNum";
    public static final String TIMELINEITEM_PROJECT_ITEM_ID = "ProjectItemId";
    public static final String TIMELINEITEM_STUDY_DAY = "StudyDay";
    public static final String TIMELINEITEM_SCHEDULED_DAY = "ScheduledDay";
    public static final String TIMELINEITEM_DATE_CREATED = "DateCreated";
    public static final String TIMELINEITEM_DATE_MODIFIED = "DateModified";
    public static final String TIMELINEITEM_CREATED_BY = "CreatedBy";
    public static final String TIMELINEITEM_MODIFIED_BY = "ModifiedBy";
    public static final String TIMELINEITEM_QCSTATE = "qcState";
    public static final String TIMELINEITEM_OBJECTID = "ObjectId";
    public static final String TIMELINEITEM_PROJECT_ITEM = "ProjectItem";


    public TimelineItem()
    {
    }

    public Integer getTimelineItemId()
    {
        return _timelineItemId;
    }

    public void setTimelineItemId(Integer timelineItemId)
    {
        _timelineItemId = timelineItemId;
    }

    public Integer getTimelineId()
    {
        return _timelineId;
    }

    public void setTimelineId(Integer timelineId)
    {
        _timelineId = timelineId;
    }

    public Integer getProjectItemId()
    {
        return _projectItemId;
    }

    public void setProjectItemId(Integer projectItemId)
    {
        _projectItemId = projectItemId;
    }

   public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    public Integer getStudyDay()
    {
        return _studyDay;
    }

    public void setStudyDay(Integer studyDay)
    {
        _studyDay = studyDay;
    }

    public String getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(String modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public Integer getScheduledDay()
    {
        return _scheduledDay;
    }

    public void setScheduledDay(Integer scheduledDay)
    {
        _scheduledDay = scheduledDay;
    }

    public Integer getQcState()
    {
        return _qcState;
    }

    public void setQcState(Integer qcState)
    {
        _qcState = qcState;
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

    public Integer getRevisionNum()
    {
        return _revisionNum;
    }

    public void setRevisionNum(Integer revisionNum)
    {
        _revisionNum = revisionNum;
    }

    @NotNull
    public Map<String, Object> toMap(Container c)
    {
        Map<String, Object> timelineItemValues = new ArrayListMap<>();
        timelineItemValues.put(TIMELINEITEM_TIMELINE_ITEM_ID, getTimelineItemId());
        timelineItemValues.put(TIMELINEITEM_TIMELINE_ID, getTimelineId());
        timelineItemValues.put(TIMELINEITEM_REVISION_NUM, getRevisionNum());
        timelineItemValues.put(TIMELINEITEM_PROJECT_ITEM_ID, getProjectItemId());
        timelineItemValues.put(TIMELINEITEM_STUDY_DAY, getStudyDay());
        timelineItemValues.put(TIMELINEITEM_STUDY_DAY, getScheduledDay());
        timelineItemValues.put(TIMELINEITEM_DATE_CREATED, getDateCreated());
        timelineItemValues.put(TIMELINEITEM_DATE_MODIFIED, getDateModified());
        timelineItemValues.put(TIMELINEITEM_CREATED_BY, getCreatedBy());
        timelineItemValues.put(TIMELINEITEM_MODIFIED_BY, getModifiedBy());
        timelineItemValues.put(TIMELINEITEM_QCSTATE, getQcState());
        timelineItemValues.put(TIMELINEITEM_OBJECTID, getObjectId());

//        if (TIMELINE_PROJECT_OBJECT_ID != null)
//        {
//            Map<String, Object> project = getProject().getProjectRow(c);
//            for (String key : project.keySet())
//            {
//                timelineValues.put(key, project.get(key));
//            }
//        }
        return timelineItemValues;
    }
    @NotNull
    public JSONObject toJSON(Container c)
    {
        JSONObject json = new JSONObject();

        if (getTimelineItemId() != null)
            json.put(TIMELINEITEM_TIMELINE_ITEM_ID, getTimelineItemId());
        if (getObjectId() != null)
            json.put(TIMELINEITEM_OBJECTID, getObjectId());

        json.put(TIMELINEITEM_TIMELINE_ID, getTimelineId());
        json.put(TIMELINEITEM_REVISION_NUM, getRevisionNum());
        json.put(TIMELINEITEM_PROJECT_ITEM_ID, getProjectItemId());
        json.put(TIMELINEITEM_STUDY_DAY, getStudyDay());
        json.put(TIMELINEITEM_STUDY_DAY, getScheduledDay());
        json.put(TIMELINEITEM_DATE_CREATED, getDateCreated());
        json.put(TIMELINEITEM_DATE_MODIFIED, getDateModified());
        json.put(TIMELINEITEM_CREATED_BY, getCreatedBy());
        json.put(TIMELINEITEM_MODIFIED_BY, getModifiedBy());
        json.put(TIMELINEITEM_QCSTATE, getQcState());
        json.put(TIMELINEITEM_OBJECTID, getObjectId());
        return json;
    }
}
