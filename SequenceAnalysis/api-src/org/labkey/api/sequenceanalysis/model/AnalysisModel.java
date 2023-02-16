/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis.model;

import org.json.JSONObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

/**
 * User: bimber
 * Date: 9/22/12
 * Time: 5:01 PM
 */
public interface AnalysisModel extends Serializable
{
    Integer getAnalysisId();

    Integer getRunId();

    String getContainer();

    Integer getReadset();

    Integer getAlignmentFile();

    File getAlignmentFileObject();

    ExpData getAlignmentData();

    @Deprecated
    Integer getReferenceLibrary();

    void setReferenceLibrary(Integer libraryId);

    ExpData getReferenceLibraryData(User u) throws PipelineJobException;

    File getReferenceLibraryFile(User u) throws PipelineJobException;

    String getType();

    Date getModified();

    Integer getModifiedby();

    Date getCreated();

    Integer getCreatedby();

    Integer getRowId();

    String getDescription();

    String getSynopsis();

    void setSynopsis(String synopsis);

    Integer getLibraryId();

    void setLibraryId(Integer libraryId);

    JSONObject toJSON();
}
