package org.labkey.sequenceanalysis;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.Transient;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 2/17/2015.
 */
public class ReadDataImpl implements ReadData
{
    private Integer _rowid;
    private Integer _readset;
    private String _platformUnit;
    private String _centerName;
    private Date _date;
    private Integer _fileId1;
    private Integer _fileId2;
    private String _description;
    private String _container;
    private Date _created;
    private Integer _createdBy;
    private Date _modified;
    private Integer _modifiedBy;
    private Integer _runId;
    private boolean _archived = false;
    private String sra_accession;
    private Integer _totalReads;

    private Map<Integer, File> _cachedFiles = new HashMap<>();

    public ReadDataImpl()
    {

    }

    public Integer getRowid()
    {
        return _rowid;
    }

    public void setRowid(Integer rowid)
    {
        _rowid = rowid;
    }

    public Integer getReadset()
    {
        return _readset;
    }

    public void setReadset(Integer readset)
    {
        _readset = readset;
    }

    public String getPlatformUnit()
    {
        return _platformUnit;
    }

    public void setPlatformUnit(String platformUnit)
    {
        _platformUnit = platformUnit;
    }

    public String getCenterName()
    {
        return _centerName;
    }

    public void setCenterName(String centerName)
    {
        _centerName = centerName;
    }

    public Date getDate()
    {
        return _date;
    }

    public void setDate(Date date)
    {
        _date = date;
    }

    public Integer getFileId1()
    {
        return _fileId1;
    }

    public void setFileId1(Integer fileId1)
    {
        _fileId1 = fileId1;
    }

    public Integer getFileId2()
    {
        return _fileId2;
    }

    public void setFileId2(Integer fileId2)
    {
        _fileId2 = fileId2;
    }

    public String getDescription()
    {
        return _description;
    }

    public Integer getRunId()
    {
        return _runId;
    }

    public void setRunId(Integer runId)
    {
        _runId = runId;
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

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public Integer getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(Integer createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public Integer getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(Integer modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    @Transient
    public File getFile1()
    {
        return getFile(1, _fileId1);
    }

    @Transient
    public File getFile2()
    {
        return getFile(2, _fileId2);
    }

    public void setFile(File f, int fileIdx)
    {
        _cachedFiles.put(fileIdx, f);
    }

    @Override
    public Integer getTotalReads()
    {
        if (getFileId1() == null)
        {
            return null;
        }

        if (_totalReads == null)
        {
            if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
            {
                throw new IllegalStateException("Cannot call getTotalReads() on the remote server unless this value has been cached");
            }

            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("dataid"), getFileId1());
            filter.addCondition(FieldKey.fromString("readset"), getReadset(), CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("metricvalue"), "Total Reads", CompareType.EQUAL);
            TableSelector ts = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS), PageFlowUtil.set("metricvalue"), filter, new Sort("-rowid"));
            List<Double> values = ts.getArrayList(Double.class);
            if (!values.isEmpty())
            {
                _totalReads = values.get(0).intValue();
            }
            else
            {
                _totalReads = 0;
            }
        }

        return _totalReads;
    }

    public void setTotalReads(Integer totalReads)
    {
        _totalReads = totalReads;
    }

    @Transient
    private File getFile(int fileIdx, Integer fileId)
    {
        if (isArchived())
        {
            return null;
        }

        if (_cachedFiles.containsKey(fileIdx))
        {
            return _cachedFiles.get(fileIdx);
        }

        if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
        {
            throw new IllegalArgumentException("Your code is attempting to query ReadData on a remote server using ReadData that has not been cached.  This indicates an upstream problem with the code");
        }

        File ret = null;
        if (fileId != null)
        {
            ExpData d = ExperimentService.get().getExpData(fileId);
            if (d != null)
            {
                ret = d.getFile();
            }

            if (ret != null && !ret.exists())
            {
                throw new IllegalArgumentException("File does not exist: " + ret.getPath());
            }
        }

        _cachedFiles.put(fileIdx, ret);

        return ret;
    }

    public void cacheForRemoteServer()
    {
        if (!isArchived())
        {
            getFile1();
            getFile2();
        }

        getTotalReads();
    }

    @Transient
    public boolean isPairedEnd()
    {
        return getFile2() != null;
    }

    @Override
    public boolean isArchived()
    {
        return _archived;
    }

    public void setArchived(boolean archived)
    {
        _archived = archived;
    }

    @Override
    public String getSra_accession()
    {
        return sra_accession;
    }

    public void setSra_accession(String sra_accession)
    {
        this.sra_accession = sra_accession;
    }
}
