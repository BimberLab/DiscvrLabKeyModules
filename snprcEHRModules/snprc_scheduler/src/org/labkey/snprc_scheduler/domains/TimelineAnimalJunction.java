package org.labkey.snprc_scheduler.domains;


import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;

import java.util.Date;
import java.util.Map;

/**
 * Created by thawkins on 9/19/2018.
 */
public class TimelineAnimalJunction
{

    private Integer _rowId;         //PK
    private Integer _timelineId;    // FK to snprc_scheduler.Timeline
    private Integer _timelineRevisionNum;   // FK to snprc_scheduler.Timeline
    private String _animalId;       // FK to study.assignment
    private Date _startDate;
    private Date _endDate;
    private Date _dateCreated;
    private Date _dateModified;
    private String _createdBy;
    private String _modifiedBy;
    private String _objectId;

    public static final String TIMELINE_ANIMAL_JUNCTION_TIMELINE_ID = "TimelineId";
    public static final String TIMELINE_ANIMAL_JUNCTION_TIMELINE_REVISION_NUM = "TimelineRevisionNum";
    public static final String TIMELINE_ANIMAL_JUNCTION_ANIMAL_ID = "AnimalId";
    public static final String TIMELINE_ANIMAL_JUNCTION_START_DATE = "StartDate";
    public static final String TIMELINE_ANIMAL_JUNCTION_END_DATE = "EndDate";
    public static final String TIMELINE_ANIMAL_JUNCTION_DATE_CREATED = "DateCreated";
    public static final String TIMELINE_ANIMAL_JUNCTION_DATE_MODIFIED = "DateModified";
    public static final String TIMELINE_ANIMAL_JUNCTION_CREATED_BY = "CreatedBy";
    public static final String TIMELINE_ANIMAL_JUNCTION_MODIFIED_BY = "ModifiedBy";
    public static final String TIMELINE_ANIMAL_JUNCTION_OBJECTID = "ObjectId";

    public TimelineAnimalJunction()
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

    public String getAnimalId()
    {
        return _animalId;
    }

    public void setAnimalId(String animalId)
    {
        _animalId = animalId;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public Integer getTimelineRevisionNum()
    {
        return _timelineRevisionNum;
    }

    public void setTimelineRevisionNum(Integer timelineRevisionNum)
    {
        _timelineRevisionNum = timelineRevisionNum;
    }

    public Date getStartDate()
    {
        return _startDate;
    }

    public void setStartDate(Date startDate)
    {
        _startDate = startDate;
    }

    public Date getEndDate()
    {
        return _endDate;
    }

    public void setEndDate(Date endDate)
    {
        _endDate = endDate;
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

    @NotNull
    public Map<String, Object> getTimelineAnimalJunctionRow(Container c)
    {
        Map<String, Object> values = new ArrayListMap<>();
        values.put(TIMELINE_ANIMAL_JUNCTION_TIMELINE_ID, getTimelineId());
        values.put(TIMELINE_ANIMAL_JUNCTION_TIMELINE_REVISION_NUM, getTimelineRevisionNum());
        values.put(TIMELINE_ANIMAL_JUNCTION_ANIMAL_ID, getAnimalId());
        values.put(TIMELINE_ANIMAL_JUNCTION_START_DATE, getStartDate());
        values.put(TIMELINE_ANIMAL_JUNCTION_END_DATE, getEndDate());
        values.put(TIMELINE_ANIMAL_JUNCTION_DATE_CREATED, getDateCreated());
        values.put(TIMELINE_ANIMAL_JUNCTION_DATE_MODIFIED, getDateModified());
        values.put(TIMELINE_ANIMAL_JUNCTION_CREATED_BY, getCreatedBy());
        values.put(TIMELINE_ANIMAL_JUNCTION_MODIFIED_BY, getModifiedBy());
        values.put(TIMELINE_ANIMAL_JUNCTION_OBJECTID, getObjectId());

        return values;
    }

    @NotNull
    public JSONObject toJSON(Container c)
    {
        JSONObject json = new JSONObject();
        json.put(TIMELINE_ANIMAL_JUNCTION_TIMELINE_ID, getTimelineId());
        json.put(TIMELINE_ANIMAL_JUNCTION_TIMELINE_REVISION_NUM, getTimelineRevisionNum());
        json.put(TIMELINE_ANIMAL_JUNCTION_ANIMAL_ID, getAnimalId());
        json.put(TIMELINE_ANIMAL_JUNCTION_START_DATE, getStartDate());
        json.put(TIMELINE_ANIMAL_JUNCTION_END_DATE, getEndDate());
        json.put(TIMELINE_ANIMAL_JUNCTION_DATE_CREATED, getDateCreated());
        json.put(TIMELINE_ANIMAL_JUNCTION_DATE_MODIFIED, getDateModified());
        json.put(TIMELINE_ANIMAL_JUNCTION_CREATED_BY, getCreatedBy());
        json.put(TIMELINE_ANIMAL_JUNCTION_MODIFIED_BY, getModifiedBy());
        json.put(TIMELINE_ANIMAL_JUNCTION_OBJECTID, getObjectId());

        return json;
    }
}
