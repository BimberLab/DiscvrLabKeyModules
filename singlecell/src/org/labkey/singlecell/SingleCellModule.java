/*
 * Copyright (c) 2020 LabKey Corporation
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

package org.labkey.singlecell;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.buttons.ShowBulkEditButton;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.singlecell.analysis.CellHashingHandler;
import org.labkey.singlecell.analysis.CellRangerCellHashingHandler;
import org.labkey.singlecell.analysis.CellRangerRawDataHandler;
import org.labkey.singlecell.analysis.CellRangerSeuratHandler;
import org.labkey.singlecell.analysis.CiteSeqHandler;
import org.labkey.singlecell.analysis.SeuratCellHashingHandler;
import org.labkey.singlecell.analysis.SeuratCiteSeqHandler;
import org.labkey.singlecell.button.CellHashingButton;
import org.labkey.singlecell.button.CiteSeqButton;
import org.labkey.singlecell.run.CellRangerVDJWrapper;
import org.labkey.singlecell.run.CellRangerWrapper;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class SingleCellModule extends ExtendedSimpleModule
{
    public static final String NAME = "SingleCell";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return 20.001;
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
        addController(SingleCellController.NAME, SingleCellController.class);
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    protected void registerSchemas()
    {
        SingleCellUserSchema.register(this);
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(SingleCellSchema.NAME);
    }

    @Override
    protected void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        super.doStartupAfterSpringConfig(moduleContext);

        LaboratoryService.get().registerDataProvider(new SingleCellProvider(this));
        SequenceAnalysisService.get().registerDataProvider(new SingleCellProvider(this));

        LDKService.get().registerQueryButton(new CellHashingButton(), SingleCellSchema.SEQUENCE_SCHEMA_NAME, SingleCellSchema.TABLE_READSETS);
        LDKService.get().registerQueryButton(new CiteSeqButton(), SingleCellSchema.SEQUENCE_SCHEMA_NAME, SingleCellSchema.TABLE_READSETS);

        LaboratoryService.get().registerTableCustomizer(this, SingleCellTableCustomizer.class, SingleCellSchema.NAME, SingleCellSchema.TABLE_SAMPLES);
        LaboratoryService.get().registerTableCustomizer(this, SingleCellTableCustomizer.class, SingleCellSchema.NAME, SingleCellSchema.TABLE_SORTS);
        LaboratoryService.get().registerTableCustomizer(this, SingleCellTableCustomizer.class, SingleCellSchema.NAME, SingleCellSchema.TABLE_CDNAS);

        LDKService.get().registerQueryButton(new ShowBulkEditButton(this, SingleCellSchema.NAME, SingleCellSchema.TABLE_CDNAS), SingleCellSchema.NAME, SingleCellSchema.TABLE_CDNAS);
        LDKService.get().registerQueryButton(new ShowBulkEditButton(this, SingleCellSchema.NAME, SingleCellSchema.TABLE_SORTS), SingleCellSchema.NAME, SingleCellSchema.TABLE_SORTS);
    }

    public static void registerPipelineSteps()
    {
        CellHashingService.setInstance(CellHashingServiceImpl.get());

        SequencePipelineService.get().registerPipelineStep(new CellRangerWrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new CellRangerVDJWrapper.VDJProvider());

        SequenceAnalysisService.get().registerReadsetHandler(new CellHashingHandler());
        SequenceAnalysisService.get().registerReadsetHandler(new CiteSeqHandler());

        SequenceAnalysisService.get().registerFileHandler(new CellRangerCellHashingHandler());
        SequenceAnalysisService.get().registerFileHandler(new SeuratCellHashingHandler());
        SequenceAnalysisService.get().registerFileHandler(new SeuratCiteSeqHandler());
        SequenceAnalysisService.get().registerFileHandler(new CellRangerSeuratHandler());
        SequenceAnalysisService.get().registerFileHandler(new CellRangerRawDataHandler());
    }
}