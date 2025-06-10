package org.labkey.api.studies.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.AbstractRole;

public class StudiesDataAdminRole extends AbstractRole
{
    public StudiesDataAdminRole()
    {
        super("StudiesDataAdmin", "These users can administer data from the studies module", ReadPermission.class, InsertPermission.class, UpdatePermission.class, DeletePermission.class, StudiesDataAdminPermission.class);
    }

    @Override
    public @NotNull String getDisplayName()
    {
        return "Studies Data Admin";
    }
}
