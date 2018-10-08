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

package org.labkey.snprc_scheduler;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.snprc_scheduler.SNPRC_schedulerService;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.snprc_scheduler.security.SNPRC_schedulerEditorsRole;
import org.labkey.snprc_scheduler.security.SNPRC_schedulerReadersRole;
import org.labkey.snprc_scheduler.view.SchedulerWebPart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class SNPRC_schedulerModule extends DefaultModule
{
    public static final String NAME = "SNPRC_scheduler";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 18.21;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        Collection<WebPartFactory> webPartFactories = new ArrayList<WebPartFactory>();
        webPartFactories.add(new BaseWebPartFactory("SNPRC Scheduler", WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new SchedulerWebPart();
            }
        });

        return webPartFactories;
    }


    @Override
    protected void init()
    {

        addController(SNPRC_schedulerController.NAME, SNPRC_schedulerController.class);

        ServiceRegistry.get().registerService(SNPRC_schedulerService.class, SNPRC_schedulerServiceImpl.INSTANCE);

        // Security Roles
        RoleManager.registerRole(new SNPRC_schedulerReadersRole(), false);
        RoleManager.registerRole(new SNPRC_schedulerEditorsRole(), false);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new SNPRC_schedulerContainerListener());
        
        for (final String schemaName : getSchemaNames())
        {
            final DbSchema dbSchema = DbSchema.get(schemaName, DbSchemaType.Module);
            DefaultSchema.registerProvider(dbSchema.getQuerySchemaName(), new DefaultSchema.SchemaProvider(this)
            {
                public QuerySchema createSchema(final DefaultSchema schema, Module module)
                {
                    DbSchema dbSchema = DbSchema.get(schemaName, DbSchemaType.Module);
                    return QueryService.get().createSimpleUserSchema(dbSchema.getQuerySchemaName(), null, schema.getUser(), schema.getContainer(), dbSchema);
                }
            });
        }
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
        return Collections.singleton(SNPRC_schedulerSchema.NAME);
    }
}