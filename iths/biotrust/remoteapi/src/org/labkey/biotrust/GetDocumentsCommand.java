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
package org.labkey.biotrust;

import org.json.simple.JSONObject;
import org.labkey.remoteapi.Command;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/22/13
 */
public class GetDocumentsCommand extends Command<GetDocumentsResponse>
{

    public enum OwnerType {
        study,
        tissue,
        samplerequest
    }

    private OwnerType _ownerType = OwnerType.study;
    private String _documentTypeName;
    private int _rowId = -1;

    public GetDocumentsCommand()
    {
        super("biotrust", "getDocumentSet");
    }

    protected GetDocumentsCommand(GetDocumentsCommand source)
    {
        super(source);

        this._rowId = source._rowId;
        this._documentTypeName = source._documentTypeName;
        this._ownerType = source._ownerType;
    }

    public void setSampleRequestId(int id)
    {
        _rowId = id;
    }

    public void setStudyId(int id)
    {
        _rowId = id;
    }

    public void setOwnerType(OwnerType ownerType)
    {
        _ownerType = ownerType;
    }

    public void setDocumentTypeName(String documentTypeName)
    {
        _documentTypeName = documentTypeName;
    }

    public Map<String, Object> getParameters()
    {
        Map<String,Object> params = new HashMap<>();

        params.put("rowId", _rowId);
        params.put("ownerType", _ownerType.name());

        if (_documentTypeName != null)
            params.put("documentTypeName", _documentTypeName);

        return params;
    }

    @Override
    protected GetDocumentsResponse createResponse(String text, int status, String contentType, JSONObject json)
    {
        return new GetDocumentsResponse(text, status, contentType, json, this.copy());
    }

    @Override
    public GetDocumentsCommand copy()
    {
        return new GetDocumentsCommand(this);
    }
}
