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
package org.labkey.onprc_billing.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.security.EHRDataEntryPermission;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.table.ContainerScopedTable;
import org.labkey.api.ldk.table.CustomPermissionsTable;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.onprc_billing.ONPRC_BillingSchema;
import org.labkey.onprc_billing.security.ONPRCAliasEditorPermission;
import org.labkey.onprc_billing.security.ONPRCBillingAdminPermission;

/**
 * User: bimber
 * Date: 1/9/13
 * Time: 1:23 PM
 */
public class ONPRC_EHRBillingUserSchema extends SimpleUserSchema
{
    public ONPRC_EHRBillingUserSchema(User user, Container container)
    {
        super(ONPRC_BillingSchema.NAME, null, user, container, DbSchema.get(ONPRC_BillingSchema.NAME));
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo schematable)
    {
        if ("miscCharges".equalsIgnoreCase(name))
        {
            CustomPermissionsTable ti = new CustomPermissionsTable(this, schematable).init();
            ti.addPermissionMapping(InsertPermission.class, EHRDataEntryPermission.class);
            ti.addPermissionMapping(UpdatePermission.class, EHRDataEntryPermission.class);
            //NOTE: we really should do a QCState-based permission scheme, or filter based on whether that item was billed or not
            ti.addPermissionMapping(DeletePermission.class, EHRDataEntryPermission.class);
            return ti;
        }
        else if (ONPRC_BillingSchema.TABLE_CREDIT_GRANTS.equalsIgnoreCase(name))
        {
            ContainerScopedTable ti = new ContainerScopedTable(this, schematable, "grantNumber");
            return ti.init();
        }
        else if (ONPRC_BillingSchema.TABLE_ALIASES.equalsIgnoreCase(name))
        {
            ContainerScopedTable ti = new ContainerScopedTable(this, schematable, "alias").init();
            ti.addPermissionMapping(InsertPermission.class, ONPRCBillingAdminPermission.class);
            ti.addPermissionMapping(UpdatePermission.class, ONPRCBillingAdminPermission.class);
            ti.addPermissionMapping(DeletePermission.class, ONPRCBillingAdminPermission.class);

            return ti;
        }
        else if (ONPRC_BillingSchema.TABLE_PROJECT_ACCOUNT_HISTORY.equalsIgnoreCase(name))
        {
            CustomPermissionsTable ti = new CustomPermissionsTable(this, schematable).init();

            ti.addPermissionMapping(InsertPermission.class, ONPRCAliasEditorPermission.class);
            ti.addPermissionMapping(UpdatePermission.class, ONPRCAliasEditorPermission.class);
            ti.addPermissionMapping(DeletePermission.class, ONPRCAliasEditorPermission.class);
            return ti;
        }
        else
        {
            CustomPermissionsTable ti = new CustomPermissionsTable(this, schematable).init();

            ti.addPermissionMapping(InsertPermission.class, ONPRCBillingAdminPermission.class);
            ti.addPermissionMapping(UpdatePermission.class, ONPRCBillingAdminPermission.class);
            ti.addPermissionMapping(DeletePermission.class, ONPRCBillingAdminPermission.class);
            return ti;
        }
    }

    @Override
    protected boolean canReadSchema()
    {
        User user = getUser();
        if (user == null)
            return false;
        return getContainer().hasPermission(user, ReadPermission.class);
    }
}
