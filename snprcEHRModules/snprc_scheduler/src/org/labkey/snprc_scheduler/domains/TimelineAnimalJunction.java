package org.labkey.snprc_scheduler.domains;


import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;

import java.util.Map;

/**
 * Created by thawkins on 9/19/2018.
 */
public class TimelineAnimalJunction
{

        private Integer _timelineId;
        private String _animalId;
        private String _objectId;

        public static final String TIMELINE_ANIMAL_JUNCTION_TIMELINE_ID = "TimelineId";
        public static final String TIMELINE_ANIMAL_JUNCTION_ANIMAL_ID = "AnimalId";
        public static final String TIMELINE_ANIMAL_JUNCTION_OBJECTID = "ObjectId";
        public static final String TIMELINE_ANIMAL_JUNCTION_CONTAINER = "Container";

    public TimelineAnimalJunction(@NotNull Integer timelineId, @NotNull String animalId, String objectId, String container)
    {
        _timelineId = timelineId;
        _animalId = animalId;
        _objectId = objectId;
    }

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


    @NotNull
    public Map<String, Object> getTimelineAnimalJunctionRow(Container c)
    {
        Map<String, Object> values = new ArrayListMap<>();
        values.put(TIMELINE_ANIMAL_JUNCTION_TIMELINE_ID, getTimelineId());
        values.put(TIMELINE_ANIMAL_JUNCTION_ANIMAL_ID, getAnimalId());
        values.put(TIMELINE_ANIMAL_JUNCTION_OBJECTID, getObjectId());
        values.put(TIMELINE_ANIMAL_JUNCTION_CONTAINER, c.getId());

        return values;
    }
    @NotNull
    public JSONObject toJSON(Container c)
    {
        JSONObject json = new JSONObject();
        json.put(TIMELINE_ANIMAL_JUNCTION_TIMELINE_ID, getTimelineId());
        json.put(TIMELINE_ANIMAL_JUNCTION_ANIMAL_ID, getAnimalId());
        json.put(TIMELINE_ANIMAL_JUNCTION_OBJECTID, getObjectId());
        json.put(TIMELINE_ANIMAL_JUNCTION_CONTAINER, c.getId());

        return json;
    }
}
