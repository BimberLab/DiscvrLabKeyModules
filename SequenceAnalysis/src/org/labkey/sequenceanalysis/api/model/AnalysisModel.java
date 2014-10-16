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

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.util.Date;

/**
 * User: bimber
 * Date: 9/22/12
 * Time: 5:01 PM
 */
public interface AnalysisModel
{
    public Integer getAnalysisId();

    public Integer getRunId();

    public String getContainer();

    public Integer getReadset();

    public Integer getAlignmentFile();

    public File getAlignmentFileObject();

    public ExpData getAlignmentData();

    @Deprecated
    public Integer getReferenceLibrary();

    public void setReferenceLibrary(Integer libraryId);

    public ExpData getReferenceLibraryData();

    public File getReferenceLibraryFile();

    public String getType();

    public Date getModified();

    public Integer getModifiedby();

    public Date getCreated();

    public Integer getCreatedby();

    public Integer getRowId();

    public String getDescription();

    public String getSynopsis();

    public void setSynopsis(String synopsis);

    public Integer getLibraryId();

    public void setLibraryId(Integer libraryId);

    public JSONObject toJSON();
}
