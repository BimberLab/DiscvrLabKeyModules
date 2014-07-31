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
package org.labkey.sequenceanalysis.api.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.util.Date;

/**
 * User: bimber
 * Date: 9/22/12
 * Time: 5:01 PM
 */
public class AnalysisModel
{
    private Integer _rowId;
    private String _type;
    private Integer _runId;
    private Integer _inputFile;
    private Integer _inputFile2;
    private Integer _outputFile;
    private Integer _createdby;
    private Date _created;
    private Integer _modifiedby;
    private Date _modified;
    private String _container;
    private Integer _readset;
    private Integer _alignmentFile;
    private Integer _snpFile;
    private Integer _reference_library;
    private String _description;
    private String _synopsis;

    public AnalysisModel()
    {

    }

    public static AnalysisModel getFromDb(int analysisId, User u)
    {
        TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("rowid"), analysisId);
        TableSelector ts = new TableSelector(ti, filter, null);
        AnalysisModel[] rows = ts.getArray(AnalysisModel.class);
        if (rows.length != 1)
            throw new RuntimeException("Unable to find analysis: " + analysisId);

        AnalysisModel model = rows[0];
        Container c = ContainerManager.getForId(model.getContainer());
        if (!c.hasPermission(u, ReadPermission.class))
            throw new UnauthorizedException("User : " + analysisId);

        return model;
    }

    public Integer getAnalysisId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public void setRunId(Integer runId)
    {
        _runId = runId;
    }

    public void setInputFile(Integer inputFile)
    {
        _inputFile = inputFile;
    }

    public void setInputFile2(Integer inputFile2)
    {
        _inputFile2 = inputFile2;
    }

    public void setOutputFile(Integer outputFile)
    {
        _outputFile = outputFile;
    }

    public void setCreatedby(Integer createdby)
    {
        _createdby = createdby;
    }

    public void setModifiedby(Integer modifiedby)
    {
        _modifiedby = modifiedby;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public void setReadset(Integer readset)
    {
        _readset = readset;
    }

    public void setAlignmentFile(Integer alignmentFile)
    {
        _alignmentFile = alignmentFile;
    }

    public void setSnpFile(Integer snpFile)
    {
        _snpFile = snpFile;
    }

    public void setReference_library(Integer reference_library)
    {
        _reference_library = reference_library;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public Integer getRunId()
    {
        return _runId;
    }

    public Integer getInputFile()
    {
        return _inputFile;
    }

    public Integer getInputFile2()
    {
        return _inputFile2;
    }

    public String getContainer()
    {
        return _container;
    }

    public Integer getReadset()
    {
        return _readset;
    }

    public Integer getAlignmentFile()
    {
        return _alignmentFile;
    }

    public ExpData getAlignmentData()
    {
        if (_alignmentFile == null)
        {
            return null;
        }

        return ExperimentService.get().getExpData(_alignmentFile);
    }

    public Integer getReference_library()
    {
        return _reference_library;
    }

    public ExpData getReferenceLibraryData()
    {
        if (_reference_library == null)
        {
            return null;
        }

        return ExperimentService.get().getExpData(_reference_library);
    }

    public String getType()
    {
        return _type;
    }

    public Date getModified()
    {
        return _modified;
    }

    public Integer getModifiedby()
    {
        return _modifiedby;
    }

    public Date getCreated()
    {
        return _created;
    }

    public Integer getCreatedby()
    {
        return _createdby;
    }

    public Integer getOutputFile()
    {
        return _outputFile;
    }

    public Integer getSnpFile()
    {
        return _snpFile;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getSynopsis()
    {
        return _synopsis;
    }

    public void setSynopsis(String synopsis)
    {
        _synopsis = synopsis;
    }
}
