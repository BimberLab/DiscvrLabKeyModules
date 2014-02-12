package org.labkey.laboratory.security;

import org.labkey.api.data.Container;
import org.labkey.api.laboratory.security.LaboratoryAdminPermission;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.laboratory.LaboratoryModule;

/**

 */
public class LaboratoryAdminRole extends AbstractRole
{
    public LaboratoryAdminRole()
    {
        super("Laboratory Admin", "Grants users the ability to manage folder-level settings in the laboratory module.",
                ReadPermission.class,
                InsertPermission.class,
                UpdatePermission.class,
                DeletePermission.class,
                LaboratoryAdminPermission.class
        );
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container ? ((Container)resource).getActiveModules().contains(ModuleLoader.getInstance().getModule(LaboratoryModule.class)) : false;
    }
}
