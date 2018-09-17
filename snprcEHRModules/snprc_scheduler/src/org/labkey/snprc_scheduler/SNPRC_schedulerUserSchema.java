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
import org.labkey.snprc_scheduler.security.SNPRC_schedulerEditorsPermission;


/**
 * Created by thawkins on 19/14/2018.
 */

public class SNPRC_schedulerUserSchema extends SimpleUserSchema
{
    public SNPRC_schedulerUserSchema(User user, Container container)
    {
        super(SNPRC_schedulerSchema.NAME, null, user, container, SNPRC_schedulerSchema.getInstance().getSchema());
    }

    protected TableInfo createWrappedTable(String name, @NotNull TableInfo schemaTable)
    {
        String nameLowercased = name.toLowerCase();
        switch(nameLowercased){
            case SNPRC_schedulerSchema.TABLE_NAME_TIMELINE:
            case SNPRC_schedulerSchema.TABLE_NAME_TIMELINE_ITEM:
                return getCustomPermissionTable(createSourceTable(nameLowercased), SNPRC_schedulerEditorsPermission.class);
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
