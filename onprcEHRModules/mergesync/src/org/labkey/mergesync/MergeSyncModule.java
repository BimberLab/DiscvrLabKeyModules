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

package org.labkey.mergesync;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class MergeSyncModule extends ExtendedSimpleModule
{
    public static final String NAME = "MergeSync";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 0.11;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController(MergeSyncController.NAME, MergeSyncController.class);
    }

    @Override
    protected void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        Resource r = getModuleResource("/scripts/mergesync/mergesync.js");
        assert r != null;
        EHRService.get().registerTriggerScript(this, r);
        DetailsURL details = DetailsURL.fromString("/mergeSync/begin.view", ContainerManager.getSharedContainer());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "merge sync admin", details.getActionURL(), AdminPermission.class);

        MergeSyncManager.get().init();

        EHRService.get().registerTableCustomizer(this, MergeSyncTableCustomizer.class);
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(MergeSyncSchema.NAME);
    }

    @Override
    protected void registerSchemas()
    {
        MergeSyncUserSchema.register(this);
    }
}