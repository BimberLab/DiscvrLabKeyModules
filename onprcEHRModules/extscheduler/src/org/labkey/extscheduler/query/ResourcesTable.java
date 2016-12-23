package org.labkey.extscheduler.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

public class ResourcesTable extends FilteredTable<ExtSchedulerQuerySchema>
{
    public ResourcesTable(TableInfo table, ExtSchedulerQuerySchema schema)
    {
        super(table, schema);
        wrapAllColumns(true);

        if (!getContainer().hasPermission(schema.getUser(), AdminPermission.class))
        {
            setImportURL(AbstractTableInfo.LINK_DISABLER);
            setInsertURL(AbstractTableInfo.LINK_DISABLER);
            setUpdateURL(AbstractTableInfo.LINK_DISABLER);
            setDeleteURL(AbstractTableInfo.LINK_DISABLER);
        }
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return ReadPermission.class == perm && getContainer().hasPermission(user, perm) ||
                getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, getRealTable());
    }
}