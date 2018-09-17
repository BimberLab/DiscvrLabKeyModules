package org.labkey.snprc_scheduler.domains;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.snd.ProjectItem;

import java.util.Map;

/**
 * Created by thawkins on 9/13/2018.
 * <p>
 * Class for TimelineTable item data. Used in TimelineTable class.
 */
public class TimelineItem
{
    private Integer _timelineItemId;
    private Integer _timelineId;
    private Integer _projectItemId;
    private int _studyDay;
    private String _objectId;
    private String _container;
    private ProjectItem _projectItem;

    //private List<TimelineItem> _timelineItems = new ArrayList<>();

    public static final String TIMELINEITEM_TIMELINE_ITEM_ID = "TimelineItemId";
    public static final String TIMELINEITEM_TIMELINE_ID = "TimelineId";
    public static final String TIMELINEITEM_PROJECT_ITEM_ID = "ProjectItemId";
    public static final String TIMELINEITEM_STUDY_DAY = "StudyDay";
    public static final String TIMELINEITEM_OBJECTID = "ObjectId";
    public static final String TIMELINEITEM_CONTAINER = "Container";
    public static final String TIMELINEITEM_PROJECT_ITEM = "ProjectItem";


    public TimelineItem(@Nullable Integer timelineItemId, @NotNull Integer timelineId, @NotNull Integer projectItemId, @NotNull int studyDay, String objectId, @NotNull String container)
    {
        _timelineItemId = timelineItemId;
        _timelineId = timelineId;
        _projectItemId = projectItemId;
        _studyDay = studyDay;
        _objectId = objectId;
        _container = container;

        // TODO: get _projectItem from snd.project/snd.projectItems tables
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

    public int getStudyDay()
    {
        return _studyDay;
    }

    public void setStudyDay(int studyDay)
    {
        _studyDay = studyDay;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public ProjectItem getProjectItem()
    {
        return _projectItem;
    }

    public void setProjectItem(ProjectItem projectItem)
    {
        _projectItem = projectItem;
    }

    @NotNull
    public Map<String, Object> getTimelineItemRow(Container c)
    {
        Map<String, Object> timelineValues = new ArrayListMap<>();
        timelineValues.put(TIMELINEITEM_TIMELINE_ITEM_ID, getTimelineItemId());
        timelineValues.put(TIMELINEITEM_TIMELINE_ID, getTimelineId());
        timelineValues.put(TIMELINEITEM_PROJECT_ITEM_ID, getProjectItemId());
        timelineValues.put(TIMELINEITEM_STUDY_DAY, getStudyDay());
        timelineValues.put(TIMELINEITEM_OBJECTID, getObjectId());
        timelineValues.put(TIMELINEITEM_CONTAINER, getContainer());


//        if (TIMELINE_PROJECT_OBJECT_ID != null)
//        {
//            Map<String, Object> project = getProject().getProjectRow(c);
//            for (String key : project.keySet())
//            {
//                timelineValues.put(key, project.get(key));
//            }
//        }
        return timelineValues;
    }
    @NotNull
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = new JSONObject();
        json.put(TIMELINEITEM_TIMELINE_ID, getTimelineId());
        json.put(TIMELINEITEM_PROJECT_ITEM_ID, getProjectItemId());
        json.put(TIMELINEITEM_STUDY_DAY, getStudyDay());
        json.put(TIMELINEITEM_CONTAINER, getContainer());
        if (getTimelineItemId() != null) {
            json.put(TIMELINEITEM_TIMELINE_ITEM_ID, getTimelineItemId());
        }
        if (getObjectId() != null) {
            json.put(TIMELINEITEM_OBJECTID, getObjectId());
        }

        return json;
    }


}
