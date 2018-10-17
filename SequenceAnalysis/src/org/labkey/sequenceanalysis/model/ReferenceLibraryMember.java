package org.labkey.sequenceanalysis.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by bimber on 8/18/2014.
 */
public class ReferenceLibraryMember implements Serializable
{
    private int _rowid;
    private int _library_id;
    private Integer _ref_nt_id;
    private Integer _start;
    private Integer _stop;
    private String _type;

    private String _container;
    private String _createdby;
    private Date _created;
    private String _modifiedby;
    private Date _modified;

    transient private RefNtSequenceModel _model = null;

    public ReferenceLibraryMember()
    {

    }

    public int getRowid()
    {
        return _rowid;
    }

    public void setRowid(int rowid)
    {
        _rowid = rowid;
    }

    public int getLibraryId()
    {
        return _library_id;
    }

    public void setLibraryId(int libraryId)
    {
        _library_id = libraryId;
    }

    public Integer getRefNtId()
    {
        return _ref_nt_id;
    }

    public void setRefNtId(Integer refNtId)
    {
        _ref_nt_id = refNtId;
    }

    public int getRef_nt_id()
    {
        return _ref_nt_id;
    }

    public void setRef_nt_id(int ref_nt_id)
    {
        _ref_nt_id = ref_nt_id;
    }

    public int getLibrary_id()
    {
        return _library_id;
    }

    public void setLibrary_id(int library_id)
    {
        _library_id = library_id;
    }

    public Integer getStart()
    {
        return _start;
    }

    public void setStart(Integer start)
    {
        _start = start;
    }

    public Integer getStop()
    {
        return _stop;
    }

    public void setStop(Integer stop)
    {
        _stop = stop;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getCreatedby()
    {
        return _createdby;
    }

    public void setCreatedby(String createdby)
    {
        _createdby = createdby;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public String getModifiedby()
    {
        return _modifiedby;
    }

    public void setModifiedby(String modifiedby)
    {
        _modifiedby = modifiedby;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    @JsonIgnore
    public RefNtSequenceModel getSequenceModel()
    {
        if (_ref_nt_id == null)
        {
            return null;
        }

        if (_model == null)
        {
            _model = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), new SimpleFilter(FieldKey.fromString("rowid"), _ref_nt_id), null).getObject(RefNtSequenceModel.class);
        }

        return _model;
    }

    public String getHeaderName()
    {
        if (getSequenceModel() == null)
        {
            return null;
        }

        String name = getSequenceModel().getName();
        if (getStart() != null || getStop() != null)
        {
            if (getStop() == null)
            {
                setStop(getSequenceModel().getSequence().length());
            }

            if (getStart() == null)
            {
                setStart(1);
            }

            name = name + " (" + getStart() + "-" + getStop() + ")";
        }

        return name;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }
}
