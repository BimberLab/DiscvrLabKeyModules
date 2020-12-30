package org.labkey.api.singlecell.model;

import java.util.Date;

public class Sample
{
    private int _rowId;
    private String _subjectId;
    private String _stim;
    private String _tissue;
    private String _assaytype;
    private String _celltype;
    private Date _sampledate;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getSubjectId()
    {
        return _subjectId;
    }

    public void setSubjectId(String subjectId)
    {
        _subjectId = subjectId;
    }

    public String getStim()
    {
        return _stim;
    }

    public void setStim(String stim)
    {
        _stim = stim;
    }

    public Date getSampledate()
    {
        return _sampledate;
    }

    public void setSampledate(Date sampledate)
    {
        _sampledate = sampledate;
    }

    public String getTissue()
    {
        return _tissue;
    }

    public void setTissue(String tissue)
    {
        _tissue = tissue;
    }

    public String getAssaytype()
    {
        return _assaytype;
    }

    public void setAssaytype(String assaytype)
    {
        _assaytype = assaytype;
    }

    public String getCelltype()
    {
        return _celltype;
    }

    public void setCelltype(String celltype)
    {
        _celltype = celltype;
    }
}
