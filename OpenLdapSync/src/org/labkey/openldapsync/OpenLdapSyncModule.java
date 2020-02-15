/*
 * Copyright (c) 2018 LabKey Corporation
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

package org.labkey.openldapsync;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.openldapsync.ldap.LdapScheduler;
import org.labkey.openldapsync.ldap.LdapSyncAuditProvider;
import org.labkey.openldapsync.ldap.LdapSyncRunner;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class OpenLdapSyncModule extends SpringModule
{
    public static final String NAME = "OpenLdapSync";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 18.21;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void init()
    {
        addController(OpenLdapSyncController.NAME.toLowerCase(), OpenLdapSyncController.class);
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        DefaultSchema.registerProvider(OpenLdapSyncSchema.getInstance().getSchema().getQuerySchemaName(), new DefaultSchema.SchemaProvider(this)
        {
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                DbSchema dbSchema = DbSchema.get(OpenLdapSyncSchema.NAME, DbSchemaType.Module);
                return QueryService.get().createSimpleUserSchema(dbSchema.getQuerySchemaName(), null, schema.getUser(), schema.getContainer(), dbSchema);
            }

            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return super.isAvailable(schema, module) || schema.getContainer().equals(ContainerManager.getSharedContainer());
            }
        });

        AuditLogService.get().registerAuditType(new LdapSyncAuditProvider());

        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "ldap sync admin", DetailsURL.fromString("/openldapsync/ldapSettings.view").getActionURL(), AdminOperationsPermission.class);

        LdapScheduler.get().schedule();

        //NOTE: in order to ensure our admin queries are enabled, always turn on in the shared folder
        Set<Module> modules = ContainerManager.getSharedContainer().getActiveModules();
        if (!modules.contains(this))
        {
            modules = new HashSet<>(modules);
            modules.add(this);
            ContainerManager.getSharedContainer().setActiveModules(modules);
        }
    }

    @Override
    protected @NotNull Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    public @NotNull Collection<String> getSchemaNames()
    {
        return Collections.unmodifiableSet(Collections.singleton(OpenLdapSyncSchema.NAME));
    }

    @Override
    public @NotNull Set<Class> getIntegrationTests()
    {
        return PageFlowUtil.set(LdapSyncRunner.TestCase.class);
    }
}