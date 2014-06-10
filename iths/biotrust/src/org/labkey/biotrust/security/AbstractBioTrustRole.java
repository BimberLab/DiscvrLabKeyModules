/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.biotrust.security;

import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.biotrust.BioTrustModule;
import org.labkey.biotrust.BioTrustRCFolderType;
import org.labkey.biotrust.SpecimenRequestorFolderType;

/**
 * User: klum
 * Date: 1/22/13
 */
public class AbstractBioTrustRole extends AbstractRole
{
    @SafeVarargs
    protected AbstractBioTrustRole(String name, String description, Class<? extends Permission>... perms)
    {
        super(name, description, BioTrustModule.class, perms);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container && isBioTrustContainer((Container) resource);
    }

    private boolean isBioTrustContainer(Container container)
    {
        FolderType folderType = container.getFolderType();

        return folderType.getName().equals(BioTrustRCFolderType.NAME) || folderType.getName().equals(SpecimenRequestorFolderType.NAME);
    }

    public boolean isContactRole()
    {
        return false;
    }
}
