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

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * User: bimber
 * Date: 11/24/12
 * Time: 11:08 PM
 */
public interface Readset extends Serializable
{
    Integer getSampleId();

    String getSubjectId();

    Date getSampleDate();

    String getPlatform();

    String getApplication();

    String getChemistry();

    String getSampleType();

    String getLibraryType();

    String getName();

    Integer getInstrumentRunId();

    Integer getReadsetId();

    String getBarcode5();

    String getBarcode3();

    Double getFragmentSize();

    Double getConcentration();

    int getRowId();

    String getContainer();

    Date getCreated();

    Integer getCreatedBy();

    Date getModified();

    Integer getModifiedBy();

    String getComments();

    String getStatus();

    Integer getRunId();

    boolean hasPairedData();

    List<? extends ReadData> getReadData();
}
