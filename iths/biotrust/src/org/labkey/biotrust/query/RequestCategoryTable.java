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
package org.labkey.biotrust.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: cnathe
 * Date: 1/14/13
 */
public class RequestCategoryTable extends FilteredTable<BioTrustQuerySchema>
{
    public RequestCategoryTable(TableInfo ti, BioTrustQuerySchema schema)
    {
        super(ti, schema);

        wrapAllColumns(true);

        List<FieldKey> defaultColumns = new ArrayList<>(Arrays.asList(
                FieldKey.fromParts("Category"),
                FieldKey.fromParts("SortOrder")
        ));
        setDefaultVisibleColumns(defaultColumns);

        if (!hasPermission(schema.getUser(), AdminPermission.class))
        {
            setInsertURL(AbstractTableInfo.LINK_DISABLER);
            setUpdateURL(AbstractTableInfo.LINK_DISABLER);
            setDeleteURL(AbstractTableInfo.LINK_DISABLER);
            setImportURL(AbstractTableInfo.LINK_DISABLER);
        }
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new AdminQueryUpdateService(this, table);
        return null;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }
}
