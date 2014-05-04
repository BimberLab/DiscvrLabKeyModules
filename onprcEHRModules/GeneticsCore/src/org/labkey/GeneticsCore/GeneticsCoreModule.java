/*
 * Copyright (c) 2012 LabKey Corporation
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

package org.labkey.GeneticsCore;

import org.json.JSONObject;
import org.labkey.GeneticsCore.notification.GeneticsCoreNotification;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.ldk.notification.NotificationService;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GeneticsCoreModule extends ExtendedSimpleModule
{
    public static final String NAME = "GeneticsCore";
    public static final String CONTROLLER_NAME = "geneticscore";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 0.02;
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    protected void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        SimpleButtonConfigFactory btn1 = new SimpleButtonConfigFactory(this, "Add Genetics Blood Draw Flags", "GeneticsCore.buttons.addGeneticsFlagsForSamples(dataRegionName, arguments[0] ? arguments[0].ownerCt : null)");
        btn1.setClientDependencies(ClientDependency.fromModuleName("laboratory"), ClientDependency.fromModuleName("ehr"), ClientDependency.fromFilePath("geneticscore/window/ManageFlagsWindow.js"), ClientDependency.fromFilePath("geneticscore/buttons.js"));
        LaboratoryService.get().registerQueryButton(btn1, "laboratory", "samples");

        NotificationService.get().registerNotification(new GeneticsCoreNotification());
    }

    @Override
    protected void init()
    {
        addController(CONTROLLER_NAME, GeneticsCoreController.class);
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
        return Collections.emptySet();
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return Collections.emptySet();
    }
}
