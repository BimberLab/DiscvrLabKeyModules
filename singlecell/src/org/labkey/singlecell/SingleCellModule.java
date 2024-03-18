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
import org.labkey.api.singlecell.pipeline.SingleCellRawDataStep;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.singlecell.analysis.AbstractSingleCellHandler;
import org.labkey.singlecell.analysis.CellRangerRawDataHandler;
import org.labkey.singlecell.analysis.ProcessSeuratObjectHandler;
import org.labkey.singlecell.analysis.ProcessSingleCellHandler;
import org.labkey.singlecell.analysis.SingleCellReadsetListener;
import org.labkey.singlecell.button.FeatureBarcodeButton;
import org.labkey.singlecell.pipeline.singlecell.*;
import org.labkey.singlecell.run.CellRangerFeatureBarcodeHandler;
import org.labkey.singlecell.run.CellRangerGexCountStep;
import org.labkey.singlecell.run.CellRangerVDJWrapper;
import org.labkey.singlecell.run.CellRangerVLoupeRepairHandler;
import org.labkey.singlecell.run.NimbleAlignmentStep;
import org.labkey.singlecell.run.NimbleAnalysis;
import org.labkey.singlecell.run.VelocytoAlignmentStep;
import org.labkey.singlecell.run.VelocytoAnalysisStep;

import java.util.Arrays;
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
        return 20.006;
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

        LDKService.get().registerQueryButton(new FeatureBarcodeButton(), SingleCellSchema.SEQUENCE_SCHEMA_NAME, SingleCellSchema.TABLE_READSETS);

        LaboratoryService.get().registerTableCustomizer(this, SingleCellTableCustomizer.class, SingleCellSchema.NAME, SingleCellSchema.TABLE_SAMPLES);
        LaboratoryService.get().registerTableCustomizer(this, SingleCellTableCustomizer.class, SingleCellSchema.NAME, SingleCellSchema.TABLE_SORTS);
        LaboratoryService.get().registerTableCustomizer(this, SingleCellTableCustomizer.class, SingleCellSchema.NAME, SingleCellSchema.TABLE_CDNAS);
        LaboratoryService.get().registerTableCustomizer(this, SingleCellTableCustomizer.class, SingleCellSchema.SEQUENCE_SCHEMA_NAME, SingleCellSchema.TABLE_READSETS);

        LDKService.get().registerQueryButton(new ShowBulkEditButton(this, SingleCellSchema.NAME, SingleCellSchema.TABLE_CDNAS), SingleCellSchema.NAME, SingleCellSchema.TABLE_CDNAS);
        LDKService.get().registerQueryButton(new ShowBulkEditButton(this, SingleCellSchema.NAME, SingleCellSchema.TABLE_SORTS), SingleCellSchema.NAME, SingleCellSchema.TABLE_SORTS);

        SequenceAnalysisService.get().registerAccessoryFileProvider((f) -> {
            if (f.getName().toLowerCase().endsWith(".rds"))
            {
                return Arrays.asList(
                    CellHashingServiceImpl.get().getMetaTableFromSeurat(f, false),
                    CellHashingServiceImpl.get().getCellBarcodesFromSeurat(f, false)
                );
            }

            return Collections.emptyList();
        });
    }

    public static void registerPipelineSteps()
    {
        SequencePipelineService.get().registerPipelineStepType(SingleCellStep.class, SingleCellStep.STEP_TYPE);
        SequencePipelineService.get().registerPipelineStepType(SingleCellRawDataStep.class, SingleCellRawDataStep.STEP_TYPE);
        CellHashingService.setInstance(CellHashingServiceImpl.get());

        SequencePipelineService.get().registerPipelineStep(new CellRangerGexCountStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new CellRangerVDJWrapper.VDJProvider());
        SequencePipelineService.get().registerPipelineStep(new NimbleAlignmentStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new NimbleAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new VelocytoAlignmentStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new VelocytoAnalysisStep.Provider());

        SequenceAnalysisService.get().registerReadsetHandler(new CellRangerFeatureBarcodeHandler());

        SequenceAnalysisService.get().registerFileHandler(new CellRangerRawDataHandler());
        SequenceAnalysisService.get().registerFileHandler(new ProcessSingleCellHandler());
        SequenceAnalysisService.get().registerFileHandler(new ProcessSeuratObjectHandler());

        //Single-cell:
        SequencePipelineService.get().registerPipelineStep(new AppendCiteSeq.Provider());
        SequencePipelineService.get().registerPipelineStep(new DoubletFinder.Provider());
        SequencePipelineService.get().registerPipelineStep(new Downsample.Provider());
        SequencePipelineService.get().registerPipelineStep(new FilterRawCounts.Provider());
        SequencePipelineService.get().registerPipelineStep(new FindMarkers.Provider());
        SequencePipelineService.get().registerPipelineStep(new MergeSeurat.Provider());
        SequencePipelineService.get().registerPipelineStep(new NormalizeAndScale.Provider());
        SequencePipelineService.get().registerPipelineStep(new ClrNormalizeByGroup.Provider());
        SequenceAnalysisService.get().registerFileHandler(new CellRangerVLoupeRepairHandler());
        SequencePipelineService.get().registerPipelineStep(new PrepareRawCounts.Provider());
        SequenceAnalysisService.get().registerFileHandler(new VireoHandler());

        SequencePipelineService.get().registerPipelineStep(new RemoveCellCycle.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunCellHashing.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunPCA.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunPHATE.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunSingleR.Provider());
        SequencePipelineService.get().registerPipelineStep(new ClassifyTNKByExpression.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunConga.Provider());
        SequencePipelineService.get().registerPipelineStep(new FindClustersAndDimRedux.Provider());
        SequencePipelineService.get().registerPipelineStep(new SplitSeurat.Provider());
        SequencePipelineService.get().registerPipelineStep(new SubsetSeurat.Provider());
        SequencePipelineService.get().registerPipelineStep(new CiteSeqDimReduxDist.Provider());
        SequencePipelineService.get().registerPipelineStep(new CiteSeqWnn.Provider());
        SequencePipelineService.get().registerPipelineStep(new AvgExpression.Provider());
        SequencePipelineService.get().registerPipelineStep(new DimPlots.Provider());
        SequencePipelineService.get().registerPipelineStep(new DropAssays.Provider());
        SequencePipelineService.get().registerPipelineStep(new FeaturePlots.Provider());
        SequencePipelineService.get().registerPipelineStep(new CiteSeqDimReduxPca.Provider());
        SequencePipelineService.get().registerPipelineStep(new CiteSeqPlots.Provider());
        SequencePipelineService.get().registerPipelineStep(new PhenotypePlots.Provider());
        SequencePipelineService.get().registerPipelineStep(new CalculateUCellScores.Provider());
        SequencePipelineService.get().registerPipelineStep(new CalculateGeneComponentScores.Provider());
        SequencePipelineService.get().registerPipelineStep(new AppendMetadata.Provider());
        SequencePipelineService.get().registerPipelineStep(new AppendSaturation.Provider());
        SequencePipelineService.get().registerPipelineStep(new SeuratPrototype.Provider());
        SequencePipelineService.get().registerPipelineStep(new DietSeurat.Provider());
        SequencePipelineService.get().registerPipelineStep(new ClearCommands.Provider());
        SequencePipelineService.get().registerPipelineStep(new PlotAverageCiteSeqCounts.Provider());
        SequencePipelineService.get().registerPipelineStep(new DropCiteSeq.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunScGate.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunScGateBuiltin.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunCelltypist.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunCelltypistCustomModel.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunRiraClassification.Provider());
        SequencePipelineService.get().registerPipelineStep(new TrainCelltypist.Provider());
        SequencePipelineService.get().registerPipelineStep(new CheckExpectations.Provider());
        SequencePipelineService.get().registerPipelineStep(new CommonFilters.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunVision.Provider());
        SequencePipelineService.get().registerPipelineStep(new AppendNimble.Provider());
        SequencePipelineService.get().registerPipelineStep(new AppendTcr.Provider());
        SequencePipelineService.get().registerPipelineStep(new TcrFilter.Provider());
        SequencePipelineService.get().registerPipelineStep(new CellBarcodeFilter.Provider());
        SequencePipelineService.get().registerPipelineStep(new PlotAssayFeatures.Provider());
        SequencePipelineService.get().registerPipelineStep(new IntegrateData.Provider());
        SequencePipelineService.get().registerPipelineStep(new CustomUCell.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunSDA.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunLDA.Provider());
        SequencePipelineService.get().registerPipelineStep(new FilterDisallowedClasses.Provider());
        SequencePipelineService.get().registerPipelineStep(new SummarizeTCellActivation.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunScMetabolism.Provider());
        SequencePipelineService.get().registerPipelineStep(new ScoreCellCycle.Provider());
        SequencePipelineService.get().registerPipelineStep(new TrainScTour.Provider());
        SequencePipelineService.get().registerPipelineStep(new PredictScTour.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunEscape.Provider());
        SequencePipelineService.get().registerPipelineStep(new RunCsCore.Provider());
        SequencePipelineService.get().registerPipelineStep(new CustomGSEA.Provider());
        SequencePipelineService.get().registerPipelineStep(new StudyMetadata.Provider());

        SequenceAnalysisService.get().registerReadsetListener(new SingleCellReadsetListener());
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return PageFlowUtil.set(
                AbstractSingleCellHandler.TestCase.class,
                PrepareRawCounts.TestCase.class
        );
    }
}