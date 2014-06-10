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
import org.labkey.api.survey.SurveyService;

import java.util.ArrayList;
import java.util.List;

/**
 * User: cnathe
 * Date: 2/28/13
 */
public class SampleRequest extends Entity
{
    private int _rowId;
    private int _studyId;
    private String _requestType;
    private List<TissueRecord> _tissueRecords = new ArrayList<>();

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getStudyId()
    {
        return _studyId;
    }

    public void setStudyId(int studyId)
    {
        _studyId = studyId;
    }

    public String getRequestType()
    {
        return _requestType;
    }

    public void setRequestType(String requestType)
    {
        _requestType = requestType;
    }

    public List<TissueRecord> getTissueRecords()
    {
        return _tissueRecords;
    }

    public void setTissueRecords(List<TissueRecord> tissueRecords)
    {
        _tissueRecords = tissueRecords;
    }
}
