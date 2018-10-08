package org.labkey.snprc_scheduler.domains;


import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;

import java.util.Map;

/**
 * Created by thawkins on 9/19/2018.
 */

public class TimelineProjectItem
{
    private Integer _timelineId;
    private String _projectItemId;
    private Integer _sortOrder;
    private String _objectId;

    public static final String TIMELINE_PROJECT_ITEMS_TIMELINE_ID = "TimelineId";
    public static final String TIMELINE_PROJECT_ITEMS_PROJECT_ITEM_ID = "PorjectItemId";
    public static final String  TIMELINE_PROJECT_ITEMS_SORT_ORDER = "SortOrder";
    public static final String TIMELINE_PROJECT_ITEMS_OBJECTID = "ObjectId";
    public static final String TIMELINE_PROJECT_ITEMS_CONTAINER = "Container";

    public TimelineProjectItem(@NotNull Integer timelineId, @NotNull String projectItemId, @NotNull Integer sortOrder, String objectId)
    {
        _timelineId = timelineId;
        _projectItemId = projectItemId;
        _sortOrder = sortOrder;
        _objectId = objectId;
    }

    public TimelineProjectItem()
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

    public String getProjectItemId()
    {
        return _projectItemId;
    }

    public void setProjectItemId(String projectItemId)
    {
        _projectItemId = projectItemId;
    }

    public Integer getSortOrder()
    {
        return _sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        _sortOrder = sortOrder;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    @NotNull
    public Map<String, Object> getTimelineProjectItemRow(Container c)
    {
        Map<String, Object> values = new ArrayListMap<>();
        values.put(TIMELINE_PROJECT_ITEMS_TIMELINE_ID, getTimelineId());
        values.put(TIMELINE_PROJECT_ITEMS_PROJECT_ITEM_ID, getProjectItemId());
        values.put(TIMELINE_PROJECT_ITEMS_SORT_ORDER, getSortOrder());
        values.put(TIMELINE_PROJECT_ITEMS_OBJECTID, getObjectId());
        values.put(TIMELINE_PROJECT_ITEMS_CONTAINER, c.getId());

        return values;
    }
    @NotNull
    public JSONObject toJSON(Container c)
    {
        JSONObject json = new JSONObject();
        json.put(TIMELINE_PROJECT_ITEMS_TIMELINE_ID, getTimelineId());
        json.put(TIMELINE_PROJECT_ITEMS_PROJECT_ITEM_ID, getProjectItemId());
        json.put(TIMELINE_PROJECT_ITEMS_SORT_ORDER, getSortOrder());
        json.put(TIMELINE_PROJECT_ITEMS_OBJECTID, getObjectId());
        json.put(TIMELINE_PROJECT_ITEMS_CONTAINER, c.getId());

        return json;
    }

}
