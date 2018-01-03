/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.laboratory.security.LaboratoryAdminPermission;
import org.labkey.api.ldk.table.QueryCache;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 1:40 PM
 */
public class SimpleSettingsItem extends QueryImportNavItem implements SettingsNavItem
{
    public SimpleSettingsItem(DataProvider provider, String schema, String query, String reportCategory, String label)
    {
        super(provider, schema, query, label, LaboratoryService.NavItemCategory.settings, reportCategory, new QueryCache());
    }

    @Override
    public boolean isImportIntoWorkbooks(Container c, User u)
    {
        return false;
    }

    @Override
    public boolean getDefaultVisibility(Container c, User u)
    {
        return true;
    }

    @Override
    @NotNull
    public Container getTargetContainer(Container c)
    {
        Container target = super.getTargetContainer(c);
        if (target.isRoot())
            target = ContainerManager.getSharedContainer();

        return target;
    }

    @Override
    public ActionURL getImportUrl(Container c, User u)
    {
        if (!getTargetContainer(c).hasPermission(u, LaboratoryAdminPermission.class))
        {
            return null;
        }

        return super.getImportUrl(c, u);
    }
}
