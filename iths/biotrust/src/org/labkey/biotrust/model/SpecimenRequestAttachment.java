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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.view.ViewContext;

import java.util.List;

/**
 * User: klum
 * Date: 1/14/13
 */
public class SpecimenRequestAttachment implements AttachmentParent
{
    private int _ownerId;
    private OwnerType _ownerType;
    private int _documentTypeId;
    private Container _container;
    private String _entityId;
    private boolean _required;

    public enum OwnerType {
        study,
        tissue,
        samplerequest
    }

    public SpecimenRequestAttachment(Container c, @Nullable String entityId, int ownerId, OwnerType type,
                                     int documentTypeId)
    {
        this(c, entityId, ownerId, type, documentTypeId, false);
    }

    public SpecimenRequestAttachment(Container c, @Nullable String entityId, int ownerId, OwnerType type,
                                     int documentTypeId, boolean required)
    {
        _container = c;
        _entityId = entityId;
        _ownerId = ownerId;
        _ownerType = type;
        _documentTypeId = documentTypeId;
        _required = required;
    }

    public int getOwnerId()
    {
        return _ownerId;
    }

    public OwnerType getOwnerType()
    {
        return _ownerType;
    }

    public int getDocumentTypeId()
    {
        return _documentTypeId;
    }

    @Override
    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }

    @Override
    public String getContainerId()
    {
        return _container.getId();
    }

    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Attachment> getAttachments()
    {
        return AttachmentService.get().getAttachments(this);
    }

    public Attachment getAttachmentByName(String name)
    {
        return AttachmentService.get().getAttachment(this, name);
    }

    public boolean isRequired()
    {
        return _required;
    }

    public void setRequired(boolean required)
    {
        _required = required;
    }
}
