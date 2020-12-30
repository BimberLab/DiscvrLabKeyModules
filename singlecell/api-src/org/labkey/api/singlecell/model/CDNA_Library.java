package org.labkey.api.singlecell.model;

import org.labkey.api.singlecell.CellHashingService;

public class CDNA_Library
{
    private int _rowId;
    private Integer _sortId;
    private String _chemistry;
    private Double _concentration;
    private String _plateId;
    private String _well;

    private Integer _readsetId;
    private Integer _tcrReadsetId;
    private Integer _hashingReadsetId;
    private String _container;

    private Sort _sortRecord;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Integer getSortId()
    {
        return _sortId;
    }

    public void setSortId(Integer sortId)
    {
        _sortId = sortId;
    }

    public String getChemistry()
    {
        return _chemistry;
    }

    public void setChemistry(String chemistry)
    {
        _chemistry = chemistry;
    }

    public Double getConcentration()
    {
        return _concentration;
    }

    public void setConcentration(Double concentration)
    {
        _concentration = concentration;
    }

    public String getPlateId()
    {
        return _plateId;
    }

    public void setPlateId(String plateId)
    {
        _plateId = plateId;
    }

    public String getWell()
    {
        return _well;
    }

    public void setWell(String well)
    {
        _well = well;
    }

    public Integer getReadsetId()
    {
        return _readsetId;
    }

    public void setReadsetId(Integer readsetId)
    {
        _readsetId = readsetId;
    }

    public Integer getTcrReadsetId()
    {
        return _tcrReadsetId;
    }

    public void setTcrReadsetId(Integer tcrReadsetId)
    {
        _tcrReadsetId = tcrReadsetId;
    }

    public Integer getHashingReadsetId()
    {
        return _hashingReadsetId;
    }

    public void setHashingReadsetId(Integer hashingReadsetId)
    {
        _hashingReadsetId = hashingReadsetId;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Sort getSortRecord()
    {
        if (_sortRecord == null)
        {
            _sortRecord = CellHashingService.get().getSortById(_sortId);
        }

        return _sortRecord;
    }

    public String getAssaySampleName()
    {
        return getPlateId() + "_" + getWell() + "_" + getSortRecord().getSampleRecord().getSubjectId() + "_" + getSortRecord().getSampleRecord().getStim() + "_" + getSortRecord().getPopulation() + (getSortRecord().getHto() == null ? "" : "_" + getSortRecord().getHto());
    }
}
