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
 * Date: 7/22/13
 */
public class DocumentProperties extends Entity
{
    private String _attachmentParentId;
    private String _documentName;
    private boolean _active;
    private String _linkedDocumentUrl;

    public String getAttachmentParentId()
    {
        return _attachmentParentId;
    }

    public void setAttachmentParentId(String attachmentParentId)
    {
        _attachmentParentId = attachmentParentId;
    }

    public String getDocumentName()
    {
        return _documentName;
    }

    public void setDocumentName(String documentName)
    {
        _documentName = documentName;
    }

    public boolean isActive()
    {
        return _active;
    }

    public void setActive(boolean active)
    {
        _active = active;
    }

    public String getLinkedDocumentUrl()
    {
        return _linkedDocumentUrl;
    }

    public void setLinkedDocumentUrl(String linkedDocumentUrl)
    {
        _linkedDocumentUrl = linkedDocumentUrl;
    }
}
