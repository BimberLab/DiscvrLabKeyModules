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

package org.labkey.sla;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ehr.buttons.EHRShowEditUIButton;
import org.labkey.api.ehr.dataentry.DefaultDataEntryFormFactory;
import org.labkey.api.ehr.security.EHRProtocolEditPermission;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.sla.dataentry.CensusFormType;
import org.labkey.sla.etl.ETL;
import org.labkey.sla.etl.ETLAuditProvider;
import org.labkey.sla.security.SLAEntryRole;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class SLAModule extends ExtendedSimpleModule
{
    public static final String NAME = "SLA";
    public static final String CONTROLLER_NAME = "sla";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 13.37;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void init()
    {
        addController(CONTROLLER_NAME, SLAController.class);

        RoleManager.registerRole(new SLAEntryRole());
    }

    @Override
    protected void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        ETL.init(1);
        AuditLogService.get().registerAuditType(new ETLAuditProvider());

        DetailsURL details = DetailsURL.fromString("/sla/etlAdmin.view", ContainerManager.getSharedContainer());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "sla etl admin", details.getActionURL(), AdminPermission.class);

        EHRService.get().registerMoreActionsButton(new EHRShowEditUIButton(this, "sla", "allowableAnimals", EHRProtocolEditPermission.class), "sla", "allowableAnimals");

        EHRService.get().registerFormType(new DefaultDataEntryFormFactory(CensusFormType.class, this));
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(SLASchema.NAME);
    }

    @Override
    protected void registerSchemas()
    {
        DefaultSchema.registerProvider(SLASchema.NAME, new DefaultSchema.SchemaProvider(this)
        {
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new SLAUserSchema(schema.getUser(), schema.getContainer());
            }
        });
    }
}