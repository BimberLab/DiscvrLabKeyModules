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
 * User: klum
 * Date: 1/14/13
 */
public class DocumentType extends Entity
{
    private int _typeId;
    private String _name;
    private String _description;
    private boolean _allowMultipleUpload;

    public boolean isNew()
    {
        return _typeId == 0;
    }

    public int getTypeId()
    {
        return _typeId;
    }

    public void setTypeId(int typeId)
    {
        _typeId = typeId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public boolean isAllowMultipleUpload()
    {
        return _allowMultipleUpload;
    }

    public void setAllowMultipleUpload(boolean allowMultipleUpload)
    {
        _allowMultipleUpload = allowMultipleUpload;
    }
}
