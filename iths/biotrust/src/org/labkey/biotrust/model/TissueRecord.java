/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.biotrust.model;

import org.labkey.api.data.Entity;

/**
 * User: cnathe
 * Date: 3/5/13
 */
public class TissueRecord extends Entity
{
    private int _rowId;
    private int _sampleId;
    private String _requestType;
    private String _bloodSampleType;
    private Boolean _surgicalPairWithBlood;

    public enum Types {

        TissueSample("Tissue"),
        BloodSample("Blood");

        private String _label;

        Types(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }
    };

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(int sampleId)
    {
        _sampleId = sampleId;
    }

    public String getRequestType()
    {
        return _requestType;
    }

    public void setRequestType(String requestType)
    {
        _requestType = requestType;
    }

    public String getBloodSampleType()
    {
        return _bloodSampleType;
    }

    public void setBloodSampleType(String bloodSampleType)
    {
        _bloodSampleType = bloodSampleType;
    }

    public Boolean getSurgicalPairWithBlood()
    {
        return _surgicalPairWithBlood;
    }

    public void setSurgicalPairWithBlood(Boolean surgicalPairWithBlood)
    {
        _surgicalPairWithBlood = surgicalPairWithBlood;
    }
}
