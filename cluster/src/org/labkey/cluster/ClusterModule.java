/*
 * Copyright (c) 2015 LabKey Corporation
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

package org.labkey.cluster;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.view.WebPartFactory;
import org.labkey.cluster.pipeline.ClusterPipelineProvider;
import org.labkey.cluster.pipeline.TestCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ClusterModule extends ExtendedSimpleModule
{
    private static final Logger _log = LogManager.getLogger(ClusterModule.class);

    public static final String NAME = "Cluster";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 15.24;
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
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController(ClusterController.NAME, ClusterController.class);
    }

    @Override
    public void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        ClusterManager.get().schedule();

        DetailsURL details = DetailsURL.fromString("/cluster/begin.view", ContainerManager.getSharedContainer());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "cluster admin", details.getActionURL());

        PipelineService.get().registerPipelineProvider(new ClusterPipelineProvider(this));
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
        return Collections.singleton(ClusterSchema.NAME);
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        @SuppressWarnings({"unchecked"})
        Set<Class> testClasses = new HashSet<>(Arrays.asList(
                TestCase.class
        ));

        return testClasses;
    }

    @Override
    protected void registerSchemas()
    {
        DbSchema dbSchema = DbSchema.get(ClusterSchema.NAME, DbSchemaType.Module);
        DefaultSchema.registerProvider(dbSchema.getQuerySchemaName(), new DefaultSchema.SchemaProvider(this)
        {
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                DbSchema dbSchema = DbSchema.get(ClusterSchema.NAME, DbSchemaType.Module);
                return QueryService.get().createSimpleUserSchema(dbSchema.getQuerySchemaName(), null, schema.getUser(), schema.getContainer(), dbSchema);
            }

            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                //make this schema available whenever pipeline module is turned on
                return super.isAvailable(schema, ModuleLoader.getInstance().getModule("Pipeline"));
            }
        });
    }
}