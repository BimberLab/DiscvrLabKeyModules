/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * User: bbimber
 * Date: 5/25/12
 * Time: 2:44 PM
 */
public class SequenceReadsetImpl implements Readset
{
    private Integer _rowId;
    private String _name;
    private String _comments;
    private String _platform;
    private Integer _sampleId;
    private String _barcode5;
    private String _barcode3;
    private String _subjectId;
    private Date _sampleDate;
    private String _sampleType;
    private String _libraryType;
    private String _application;
    private String _chemistry;
    private Double _fragmentSize;
    private Double _concentration;
    private Integer _instrument_run_id;
    private Integer _runId;
    private String _status;
    private String _container;
    private Date _created;
    private Integer _createdBy;
    private Date _modified;
    private Integer _modifiedBy;
    private List<ReadDataImpl> _readData = null;

    private String _fileSetName = null;

    public SequenceReadsetImpl()
    {

    }

    @Override
    public int getRowId()
    {
        return _rowId == null ? 0 : _rowId;
    }

    public void unsetRowId()
    {
        _rowId = null;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public boolean existsInDatabase()
    {
        return _rowId != null &&  _rowId > 0;
    }

    @Override
    @JsonIgnore
    public Integer getReadsetId()
    {
        return _rowId;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        _comments = comments;
    }

    @Override
    public String getPlatform()
    {
        return _platform;
    }

    public void setPlatform(String platform)
    {
        _platform = platform;
    }

    @Override
    public Integer getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(Integer sampleId)
    {
        _sampleId = sampleId;
    }

    @Override
    public String getBarcode5()
    {
        return _barcode5;
    }

    public void setBarcode5(String barcode5)
    {
        _barcode5 = barcode5;
    }

    @Override
    public String getBarcode3()
    {
        return _barcode3;
    }

    public void setBarcode3(String barcode3)
    {
        _barcode3 = barcode3;
    }

    @Override
    public Double getFragmentSize()
    {
        return _fragmentSize;
    }

    public void setFragmentSize(Double fragmentSize)
    {
        _fragmentSize = fragmentSize;
    }

    @Override
    public Double getConcentration()
    {
        return _concentration;
    }

    public void setConcentration(Double concentration)
    {
        _concentration = concentration;
    }

    @Override
    public String getSubjectId()
    {
        return _subjectId;
    }

    public void setSubjectId(String subjectId)
    {
        _subjectId = subjectId;
    }

    @Override
    public String getLibraryType()
    {
        return _libraryType;
    }

    public void setLibraryType(String libraryType)
    {
        _libraryType = libraryType;
    }

    @Override
    public String getApplication()
    {
        return _application;
    }

    public void setApplication(String application)
    {
        _application = application;
    }

    @Override
    public String getChemistry()
    {
        return _chemistry;
    }

    public void setChemistry(String chemistry)
    {
        _chemistry = chemistry;
    }

    @Override
    public String getSampleType()
    {
        return _sampleType;
    }

    public void setSampleType(String sampleType)
    {
        _sampleType = sampleType;
    }

    @Override
    public Integer getInstrumentRunId()
    {
        return _instrument_run_id;
    }

    public Integer getInstrument_run_id()
    {
        return _instrument_run_id;
    }

    public void setInstrumentRunId(Integer instrument_run_id)
    {
        _instrument_run_id = instrument_run_id;
    }

    public void setInstrument_run_id(Integer instrument_run_id)
    {
        _instrument_run_id = instrument_run_id;
    }

    @Override
    public Integer getRunId()
    {
        return _runId;
    }

    public void setRunId(Integer runId)
    {
        _runId = runId;
    }

    @Override
    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    @Override
    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    @Override
    public Date getSampleDate()
    {
        return _sampleDate;
    }

    public void setSampleDate(Date sampleDate)
    {
        _sampleDate = sampleDate;
    }

    @Override
    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    @Override
    public Integer getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(Integer createdBy)
    {
        _createdBy = createdBy;
    }

    @Override
    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    @Override
    public Integer getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(Integer modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    @Override
    public List<? extends ReadData> getReadData()
    {
        return getReadDataImpl();
    }

    public List<ReadDataImpl> getReadDataImpl()
    {
        if (_readData != null)
        {
            return Collections.unmodifiableList(_readData);
        }

        if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
        {
            throw new IllegalArgumentException("Your code is attempting to query ReadData from a Readset on a remote server using a readset that has not been cached.  This indicates an upstream problem with the code");
        }

        _readData = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA), new SimpleFilter(FieldKey.fromString("readset"), getReadsetId()), null).getArrayList(ReadDataImpl.class);

        return Collections.unmodifiableList(_readData);
    }

    public void setReadData(List<ReadDataImpl> readData)
    {
        _readData = readData;
    }

    public void cacheForRemoteServer()
    {
        for (ReadDataImpl d : getReadDataImpl())
        {
            d.cacheForRemoteServer();
        }
    }

    @Override
    public boolean hasPairedData()
    {
        for (ReadDataImpl d : getReadDataImpl())
        {
            if (d.isPairedEnd())
            {
                return true;
            }
        }

        return false;
    }

    // used internally during sequence read import
    public String getFileSetName()
    {
        return _fileSetName;
    }

    // used internally during sequence read import
    public void setFileSetName(String fileSetName)
    {
        _fileSetName = fileSetName;
    }

    public String getLegalFileName()
    {
        return getName().replaceAll("[^a-zA-Z0-9\\.\\-\\+]", "_");
    }

    public boolean hasArchivedData()
    {
        for (ReadData rd : getReadData())
        {
            if (rd.isArchived())
            {
                return true;
            }
        }

        return false;
    }
}
