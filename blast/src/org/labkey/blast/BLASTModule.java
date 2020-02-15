/*
 * Copyright (c) 2014 LabKey Corporation
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

package org.labkey.blast;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.WebPartFactory;
import org.labkey.blast.button.BlastOligosButton;
import org.labkey.blast.button.CreateDatabaseButton;
import org.labkey.blast.button.ReprocessDatabaseButton;
import org.labkey.blast.pipeline.BlastDatabasePipelineProvider;
import org.labkey.blast.pipeline.BlastPipelineProvider;
import org.labkey.blast.query.BlastUserSchema;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class BLASTModule extends ExtendedSimpleModule
{
    public static final String NAME = "BLAST";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 13.33;
    }

    @Override
    protected void registerSchemas()
    {
        BlastUserSchema.register(this);
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
        addController(BLASTController.NAME, BLASTController.class);
    }

    @Override
    public void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        LaboratoryService.get().registerDataProvider(new BLASTDataProvider(this));
        SequenceAnalysisService.get().registerDataProvider(new BLASTDataProvider(this));

        LDKService.get().registerQueryButton(new CreateDatabaseButton(), "sequenceanalysis", "reference_libraries");
        LDKService.get().registerQueryButton(new BlastOligosButton(), "laboratory", "dna_oligos");
        LDKService.get().registerQueryButton(new ReprocessDatabaseButton(), BLASTSchema.NAME, BLASTSchema.TABLE_DATABASES);

        SystemMaintenance.addTask(new BLASTMaintenanceTask());
        PipelineService.get().registerPipelineProvider(new BlastPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new BlastDatabasePipelineProvider(this));

        SequenceAnalysisService.get().registerGenomeTrigger(new BlastGenomeTrigger());
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
        return Collections.singleton(BLASTSchema.NAME);
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new BLASTUpgradeCode();
    }

}
