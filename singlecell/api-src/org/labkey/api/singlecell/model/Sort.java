package org.labkey.api.singlecell.model;

import org.labkey.api.singlecell.CellHashingService;

public class Sort
{
    private int _rowId;
    private Integer _sampleId;
    private String _population;
    private String _hto;

    private Sample _sampleRecord;

    public Sample getSampleRecord()
    {
        if (_sampleRecord == null)
        {
            _sampleRecord = CellHashingService.get().getSampleById(_sampleId);
        }

        return _sampleRecord;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Integer getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(Integer sampleId)
    {
        _sampleId = sampleId;
    }

    public String getPopulation()
    {
        return _population;
    }

    public void setPopulation(String population)
    {
        _population = population;
    }

    public String getHto()
    {
        return _hto;
    }

    public void setHto(String hto)
    {
        _hto = hto;
    }

    public void setSampleRecord(Sample sampleRecord)
    {
        _sampleRecord = sampleRecord;
    }
}
