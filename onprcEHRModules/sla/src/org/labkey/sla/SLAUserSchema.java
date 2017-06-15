package org.labkey.sla;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.security.EHRProtocolEditPermission;
import org.labkey.api.ldk.table.CustomPermissionsTable;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;

/**

 */
public class SLAUserSchema extends SimpleUserSchema
{
    public SLAUserSchema(User user, Container container)
    {
        super(SLASchema.NAME, null, user, container, SLASchema.getInstance().getSchema());
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo schematable)
    {
        if ("allowableAnimals".equalsIgnoreCase(name))
        {
            CustomPermissionsTable ti = new CustomPermissionsTable(this, schematable).init();
            ti.addPermissionMapping(InsertPermission.class, EHRProtocolEditPermission.class);
            ti.addPermissionMapping(UpdatePermission.class, EHRProtocolEditPermission.class);
            ti.addPermissionMapping(DeletePermission.class, EHRProtocolEditPermission.class);
            return ti;
        }

        return super.createWrappedTable(name, schematable);
    }
}
