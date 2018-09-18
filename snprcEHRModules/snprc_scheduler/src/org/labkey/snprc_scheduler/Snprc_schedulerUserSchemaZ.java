package org.labkey.snprc_scheduler;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.CustomPermissionsTable;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.snprc_scheduler.security.Snprc_schedulerEditorsPermissionZ;


/**
 * Created by thawkins on 19/14/2018.
 */

public class Snprc_schedulerUserSchemaZ extends SimpleUserSchema
{
    public Snprc_schedulerUserSchemaZ(User user, Container container)
    {
        super(Snprc_schedulerSchemaZ.NAME, null, user, container, Snprc_schedulerSchemaZ.getInstance().getSchema());
    }

    protected TableInfo createWrappedTable(String name, @NotNull TableInfo schemaTable)
    {
        String nameLowercased = name.toLowerCase();
        switch(nameLowercased){
            case Snprc_schedulerSchemaZ.TABLE_NAME_TIMELINE:
            case Snprc_schedulerSchemaZ.TABLE_NAME_TIMELINE_ITEM:
                return getCustomPermissionTable(createSourceTable(nameLowercased), Snprc_schedulerEditorsPermissionZ.class);
        }

        return super.createWrappedTable(name, schemaTable);
    }


    private TableInfo getCustomPermissionTable(TableInfo schemaTable, Class<? extends Permission> perm)
    {
        CustomPermissionsTable result = new CustomPermissionsTable(this, schemaTable);
        result.addPermissionMapping(InsertPermission.class, perm);
        result.addPermissionMapping(UpdatePermission.class, perm);
        result.addPermissionMapping(DeletePermission.class, perm);

        return result.init();
    }

}
