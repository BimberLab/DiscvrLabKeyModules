package org.labkey.api.studies.study;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.json.JSONObject;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StudyDefinition
{
    private Integer _rowId;
    private String _studyName;
    private String _label;
    private String _category;
    private String _description;

    private String _container;
    private Integer _createdBy;
    private Date _created;
    private Integer _modifiedBy;
    private Date _modified;

    private List<StudyCohort> _cohorts;
    private List<AnchorEvent> _anchorEvents;
    private List<Timepoint> _timepoints;

    public StudyDefinition()
    {

    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public String getStudyName()
    {
        return _studyName;
    }

    public void setStudyName(String studyName)
    {
        _studyName = studyName;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getCategory()
    {
        return _category;
    }

    public void setCategory(String category)
    {
        _category = category;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Integer getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(Integer createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public Integer getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(Integer modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public List<StudyCohort> getCohorts()
    {
        return _cohorts;
    }

    public void setCohorts(List<StudyCohort> cohorts)
    {
        _cohorts = cohorts;
    }

    public List<AnchorEvent> getAnchorEvents()
    {
        return _anchorEvents;
    }

    public void setAnchorEvents(List<AnchorEvent> anchorEvents)
    {
        _anchorEvents = anchorEvents;
    }

    public List<Timepoint> getTimepoints()
    {
        return _timepoints;
    }

    public void setTimepoints(List<Timepoint> timepoints)
    {
        _timepoints = timepoints;
    }

    public static class StudyCohort
    {
        private Integer _rowId;
        private Integer _studyId;

        private String _cohortName;
        private String _label;
        private String _category;
        private String _description;
        private Boolean _isControlGroup = false;
        private Integer _sortOrder;

        private String _container;
        private Integer _createdBy;
        private Date _created;

        private Integer _modifiedBy;
        private Date _modified;

        public StudyCohort()
        {

        }

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public Integer getStudyId()
        {
            return _studyId;
        }

        public void setStudyId(Integer studyId)
        {
            _studyId = studyId;
        }

        public String getCohortName()
        {
            return _cohortName;
        }

        public void setCohortName(String cohortName)
        {
            _cohortName = cohortName;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String category)
        {
            _category = category;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public Boolean getIsControlGroup()
        {
            return _isControlGroup;
        }

        public void setIsControlGroup(Boolean controlGroup)
        {
            _isControlGroup = controlGroup;
        }

        public Integer getSortOrder()
        {
            return _sortOrder;
        }

        public void setSortOrder(Integer sortOrder)
        {
            _sortOrder = sortOrder;
        }

        public String getContainer()
        {
            return _container;
        }

        public void setContainer(String container)
        {
            _container = container;
        }

        public Integer getCreatedBy()
        {
            return _createdBy;
        }

        public void setCreatedBy(Integer createdBy)
        {
            _createdBy = createdBy;
        }

        public Date getCreated()
        {
            return _created;
        }

        public void setCreated(Date created)
        {
            _created = created;
        }

        public Integer getModifiedBy()
        {
            return _modifiedBy;
        }

        public void setModifiedBy(Integer modifiedBy)
        {
            _modifiedBy = modifiedBy;
        }

        public Date getModified()
        {
            return _modified;
        }

        public void setModified(Date modified)
        {
            _modified = modified;
        }
    }

    public static class AnchorEvent
    {
        private Integer _rowId;
        private Integer _studyId;

        private String _label;
        private String _description;
        private String _eventProviderName;

        private String _container;
        private Integer _createdBy;
        private Date _created;

        private Integer _modifiedBy;
        private Date _modified;

        public AnchorEvent()
        {

        }

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public Integer getStudyId()
        {
            return _studyId;
        }

        public void setStudyId(Integer studyId)
        {
            _studyId = studyId;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getEventProviderName()
        {
            return _eventProviderName;
        }

        public void setEventProviderName(String eventProviderName)
        {
            _eventProviderName = eventProviderName;
        }

        public String getContainer()
        {
            return _container;
        }

        public void setContainer(String container)
        {
            _container = container;
        }

        public Integer getCreatedBy()
        {
            return _createdBy;
        }

        public void setCreatedBy(Integer createdBy)
        {
            _createdBy = createdBy;
        }

        public Date getCreated()
        {
            return _created;
        }

        public void setCreated(Date created)
        {
            _created = created;
        }

        public Integer getModifiedBy()
        {
            return _modifiedBy;
        }

        public void setModifiedBy(Integer modifiedBy)
        {
            _modifiedBy = modifiedBy;
        }

        public Date getModified()
        {
            return _modified;
        }

        public void setModified(Date modified)
        {
            _modified = modified;
        }
    }

    public static class Timepoint
    {
        private Integer _rowId;
        private Integer _studyId;
        private Integer _cohortId;
        private String _label;
        private String _labelShort;
        private String _description;

        @JsonIgnore
        private Integer _anchorEvent;

        @JsonIgnore
        private String _anchorEventLabel;

        private String _cohortName;
        private Integer _rangeMin;
        private Integer _rangeMax;

        private String _container;
        private Integer _createdBy;
        private Date _created;

        private Integer _modifiedBy;
        private Date _modified;

        public Timepoint()
        {

        }

        // When reading from a JSON object, store the anchorEvent label
        @JsonSetter("anchorEvent")
        void readAnchorEvent(String lbl) { _anchorEventLabel = lbl; }

        @JsonGetter("anchorEvent")
        String writeAnchorEvent() { return _anchorEventLabel; }

        // Call this to translate from label to index in order to fit the DB schema
        void resolveAnchorEvent(Map<String,Integer> idxByLabel)
        {
            Integer idx = idxByLabel.get(_anchorEventLabel);
            if (idx == null)
                throw new IllegalArgumentException(
                        "Unknown anchorEvent label '" + _anchorEventLabel + "'");
            _anchorEvent = idx;
        }

        public Integer getAnchorEvent() { return _anchorEvent; }

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public Integer getStudyId()
        {
            return _studyId;
        }

        public void setStudyId(Integer studyId)
        {
            _studyId = studyId;
        }

        public String getCohortName()
        {
            return _cohortName;
        }

        public void setCohortName(String cohortName)
        {
            _cohortName = cohortName;
        }

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getLabelShort()
        {
            return _labelShort;
        }

        public void setLabelShort(String labelShort)
        {
            _labelShort = labelShort;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public Integer getRangeMin()
        {
            return _rangeMin;
        }

        public void setRangeMin(Integer rangeMin)
        {
            _rangeMin = rangeMin;
        }

        public Integer getRangeMax()
        {
            return _rangeMax;
        }

        public void setRangeMax(Integer rangeMax)
        {
            _rangeMax = rangeMax;
        }

        public String getContainer()
        {
            return _container;
        }

        public void setContainer(String container)
        {
            _container = container;
        }

        public Integer getCreatedBy()
        {
            return _createdBy;
        }

        public void setCreatedBy(Integer createdBy)
        {
            _createdBy = createdBy;
        }

        public Date getCreated()
        {
            return _created;
        }

        public void setCreated(Date created)
        {
            _created = created;
        }

        public Integer getModifiedBy()
        {
            return _modifiedBy;
        }

        public void setModifiedBy(Integer modifiedBy)
        {
            _modifiedBy = modifiedBy;
        }

        public Date getModified()
        {
            return _modified;
        }

        public void setModified(Date modified)
        {
            _modified = modified;
        }
    }

    public static StudyDefinition fromJson(JSONObject json)
    {
        ObjectMapper mapper = new ObjectMapper();
        StudyDefinition sd = mapper.convertValue(json.toMap(), StudyDefinition.class);

        // In our JSON, Timepoints store the anchorEvent label, not an ID. Since the DB schema requires an int, we need
        // to do that translation manually. Here, we store the anchorEvent by its index in the anchorEvent list.
        Map<String,Integer> idxByLabel = IntStream.range(0, sd.getAnchorEvents().size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> sd.getAnchorEvents().get(i).getLabel(),
                        i -> i));

        sd.getTimepoints().forEach(tp -> tp.resolveAnchorEvent(idxByLabel));

        return sd;
    }

    public String toJson() throws JsonProcessingException
    {
        ObjectWriter ow = new ObjectMapper().writer();
        return ow.writeValueAsString(this);
    }

    public static StudyDefinition getForId(int studyId, User u)
    {
        // TODO: implement this. This should query the DB and return a populated StudyDefinition. It should make sure the passed user has ReadPermission on that container

        return null;
    }
}
