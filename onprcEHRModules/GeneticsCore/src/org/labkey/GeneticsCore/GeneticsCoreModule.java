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

import org.apache.log4j.Logger;
import org.labkey.GeneticsCore.analysis.CombineMethylationRatesHandler;
import org.labkey.GeneticsCore.analysis.MethylationRateComparisonHandler;
import org.labkey.GeneticsCore.button.ChangeReadsetStatusButton;
import org.labkey.GeneticsCore.button.ChangeReadsetStatusForAnalysesButton;
import org.labkey.GeneticsCore.button.HaplotypeReviewButton;
import org.labkey.GeneticsCore.button.PublishSBTResultsButton;
import org.labkey.GeneticsCore.button.SBTReviewButton;
import org.labkey.GeneticsCore.notification.GeneticsCoreNotification;
import org.labkey.GeneticsCore.pipeline.BisSnpGenotyperAnalysis;
import org.labkey.GeneticsCore.pipeline.BisSnpIndelRealignerStep;
import org.labkey.GeneticsCore.pipeline.BismarkWrapper;
import org.labkey.GeneticsCore.pipeline.BlastPipelineJobResourceAllocator;
import org.labkey.GeneticsCore.pipeline.ClusterMaintenanceTask;
import org.labkey.GeneticsCore.pipeline.ExacloudResourceSettings;
import org.labkey.GeneticsCore.pipeline.SequenceJobResourceAllocator;
import org.labkey.api.cluster.ClusterService;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.notification.NotificationService;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.template.ClientDependency;

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
        return 17.10;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        SimpleButtonConfigFactory btn1 = new SimpleButtonConfigFactory(this, "Add Genetics Blood Draw Flags", "GeneticsCore.buttons.editGeneticsFlagsForSamples(dataRegionName, arguments[0] ? arguments[0].ownerCt : null, 'add')");
        btn1.setClientDependencies(ClientDependency.fromModuleName("laboratory"), ClientDependency.fromModuleName("ehr"), ClientDependency.fromPath("geneticscore/window/ManageFlagsWindow.js"), ClientDependency.fromPath("geneticscore/buttons.js"));
        LDKService.get().registerQueryButton(btn1, "laboratory", "samples");

        SimpleButtonConfigFactory btn2 = new SimpleButtonConfigFactory(this, "Remove Genetics Blood Draw Flags", "GeneticsCore.buttons.editGeneticsFlagsForSamples(dataRegionName, arguments[0] ? arguments[0].ownerCt : null, 'remove')");
        btn2.setClientDependencies(ClientDependency.fromModuleName("laboratory"), ClientDependency.fromModuleName("ehr"), ClientDependency.fromPath("geneticscore/window/ManageFlagsWindow.js"), ClientDependency.fromPath("geneticscore/buttons.js"));
        LDKService.get().registerQueryButton(btn2, "laboratory", "samples");

        SimpleButtonConfigFactory btn3 = new SimpleButtonConfigFactory(this, "Edit Alignments", "GeneticsCore.window.EditAlignmentsWindow.buttonHandler(dataRegionName, arguments[0] ? arguments[0].ownerCt : null)");
        btn3.setClientDependencies(ClientDependency.fromModuleName("laboratory"), ClientDependency.fromModuleName("sequenceanalysis"), ClientDependency.fromPath("geneticscore/window/EditAlignmentsWindow.js"));
        LDKService.get().registerQueryButton(btn3, "sequenceanalysis", "alignment_summary_grouped");

        NotificationService.get().registerNotification(new GeneticsCoreNotification());

        LDKService.get().registerQueryButton(new SBTReviewButton(), "sequenceanalysis", "sequence_analyses");
        LDKService.get().registerQueryButton(new HaplotypeReviewButton(), "sequenceanalysis", "sequence_analyses");
        LDKService.get().registerQueryButton(new ChangeReadsetStatusForAnalysesButton(), "sequenceanalysis", "sequence_analyses");
        LDKService.get().registerQueryButton(new ChangeReadsetStatusButton(), "sequenceanalysis", "sequence_readsets");
        LDKService.get().registerQueryButton(new PublishSBTResultsButton(), "sequenceanalysis", "alignment_summary_by_lineage");
        //LDKService.get().registerQueryButton(new PublishSBTHaplotypesButton(), "sequenceanalysis", "haplotypeMatches");
        LaboratoryService.get().registerTableCustomizer(this, GeneticsTableCustomizer.class, "sequenceanalysis", "sequence_analyses");
        LaboratoryService.get().registerTableCustomizer(this, GeneticsTableCustomizer.class, "sequenceanalysis", "alignment_summary_by_lineage");
        LaboratoryService.get().registerTableCustomizer(this, GeneticsTableCustomizer.class, "sequenceanalysis", "alignment_summary_grouped");

        ClusterService.get().registerResourceAllocator(new BlastPipelineJobResourceAllocator.Factory());
        ClusterService.get().registerResourceAllocator(new SequenceJobResourceAllocator.Factory());

        SequencePipelineService.get().registerResourceSettings(new ExacloudResourceSettings());

        SystemMaintenance.addTask(new ClusterMaintenanceTask());

        //register resources
        new PipelineStartup();
    }

    @Override
    protected void init()
    {
        addController(CONTROLLER_NAME, GeneticsCoreController.class);
    }

    public static class PipelineStartup
    {
        private static final Logger _log = Logger.getLogger(PipelineStartup.class);
        private static boolean _hasRegistered = false;

        public PipelineStartup()
        {
            if (_hasRegistered)
            {
                _log.warn("GeneticsCore resources have already been registered, skipping");
            }
            else
            {
                SequencePipelineService.get().registerPipelineStep(new BismarkWrapper.Provider());
                SequencePipelineService.get().registerPipelineStep(new BismarkWrapper.MethylationExtractorProvider());

                SequencePipelineService.get().registerPipelineStep(new BisSnpIndelRealignerStep.Provider());
                SequencePipelineService.get().registerPipelineStep(new BisSnpGenotyperAnalysis.Provider());

                SequenceAnalysisService.get().registerFileHandler(new MethylationRateComparisonHandler());
                SequenceAnalysisService.get().registerFileHandler(new CombineMethylationRatesHandler());

                _hasRegistered = true;
            }
        }
    }
}
