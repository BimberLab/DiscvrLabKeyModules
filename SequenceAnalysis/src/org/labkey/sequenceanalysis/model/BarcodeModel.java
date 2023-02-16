package org.labkey.sequenceanalysis.model;

import org.json.JSONArray;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * User: bimber
 * Date: 11/25/12
 * Time: 12:01 AM
 */
public class BarcodeModel extends SequenceTag
{
    private String _tag_name;
    private String _group;
    private String _sequence;
    private Integer createdby;
    private Date created;
    private Integer modifiedby;
    private Date modified;

    public BarcodeModel()
    {

    }

    public static BarcodeModel getByName(String name)
    {
        BarcodeModel[] barcodes = getByNames(name);
        return barcodes != null && barcodes.length == 1 ? barcodes[0] : null;
    }

    public static BarcodeModel[] getByGroup(String group)
    {
        return getByGroups(Collections.singletonList(group));
    }

    public static BarcodeModel[] getByGroups(List<String> groups)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("group_name"), groups, CompareType.IN);
        TableSelector ts = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_BARCODES), filter, null);
        return ts.getArray(BarcodeModel.class);
    }

    public static BarcodeModel[] getByNames(String... names)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("tag_name"), Arrays.asList(names), CompareType.IN);
        TableSelector ts = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_BARCODES), filter, null);
        return ts.getArray(BarcodeModel.class);
    }

    public static BarcodeModel fromJSON(JSONArray json)
    {
        BarcodeModel bc = new BarcodeModel();
        bc._tag_name = json.getString(0);
        bc._sequence = json.getString(1);
        return bc;
    }

    @Override
    public String getName()
    {
        return _tag_name;
    }

    @Override
    public String getSequence()
    {
        return _sequence;
    }

    public void setName(String name)
    {
        _tag_name = name;
    }

    public void setSequence(String sequence)
    {
        _sequence = sequence;
    }

    public void setTag_name(String tag_name)
    {
        _tag_name = tag_name;
    }

    public String getGroup()
    {
        return _group;
    }

    public void setGroup(String group)
    {
        _group = group;
    }

    public Integer getCreatedby()
    {
        return createdby;
    }

    public void setCreatedby(Integer createdby)
    {
        this.createdby = createdby;
    }

    public Date getCreated()
    {
        return created;
    }

    public void setCreated(Date created)
    {
        this.created = created;
    }

    public Integer getModifiedby()
    {
        return modifiedby;
    }

    public void setModifiedby(Integer modifiedby)
    {
        this.modifiedby = modifiedby;
    }

    public Date getModified()
    {
        return modified;
    }

    public void setModified(Date modified)
    {
        this.modified = modified;
    }

//    @Override
//    public boolean equals(Object o)
//    {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//
//        BarcodeModel that = (BarcodeModel) o;
//
//        if (_sequence != null ? !_sequence.equals(that._sequence) : that._sequence != null) return false;
//        if (_tag_name != null ? !_tag_name.equals(that._tag_name) : that._tag_name != null) return false;
//
//        return true;
//    }
//
//    @Override
//    public int hashCode()
//    {
//        int result = _tag_name != null ? _tag_name.hashCode() : 0;
//        result = 31 * result + (_sequence != null ? _sequence.hashCode() : 0);
//        return result;
//    }
}
