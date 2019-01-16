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
    public Integer getSampleId();

    public String getSubjectId();

    public Date getSampleDate();

    public String getPlatform();

    public String getApplication();

    public String getChemistry();

    public String getSampleType();

    public String getLibraryType();

    public String getName();

    public Integer getInstrumentRunId();

    public Integer getReadsetId();

    public String getBarcode5();

    public String getBarcode3();

    public Double getFragmentSize();

    public Double getConcentration();

    public int getRowId();

    public String getContainer();

    public Date getCreated();

    public Integer getCreatedBy();

    public Date getModified();

    public Integer getModifiedBy();

    public String getComments();

    public String getStatus();

    public Integer getRunId();

    public boolean hasPairedData();

    public List<? extends ReadData> getReadData();
}
