/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.sequenceanalysis;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.buttons.ShowBulkEditButton;
import org.labkey.api.ldk.buttons.ShowEditUIButton;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.WebPartFactory;
import org.labkey.sequenceanalysis.analysis.BamCleanupHandler;
import org.labkey.sequenceanalysis.analysis.BamHaplotypeHandler;
import org.labkey.sequenceanalysis.analysis.CombineStarGeneCountsHandler;
import org.labkey.sequenceanalysis.analysis.CombineSubreadGeneCountsHandler;
import org.labkey.sequenceanalysis.analysis.GenotypeGVCFHandler;
import org.labkey.sequenceanalysis.analysis.HaplotypeCallerHandler;
import org.labkey.sequenceanalysis.analysis.LiftoverHandler;
import org.labkey.sequenceanalysis.analysis.ListVcfSamplesHandler;
import org.labkey.sequenceanalysis.analysis.MultiQCBamHandler;
import org.labkey.sequenceanalysis.analysis.MultiQCHandler;
import org.labkey.sequenceanalysis.analysis.PicardAlignmentMetricsHandler;
import org.labkey.sequenceanalysis.analysis.PrintReadBackedHaplotypesHandler;
import org.labkey.sequenceanalysis.analysis.RecalculateSequenceMetricsHandler;
import org.labkey.sequenceanalysis.analysis.RnaSeqcHandler;
import org.labkey.sequenceanalysis.analysis.SbtGeneCountHandler;
import org.labkey.sequenceanalysis.analysis.UnmappedSequenceBasedGenotypeHandler;
import org.labkey.sequenceanalysis.button.AddSraRunButton;
import org.labkey.sequenceanalysis.button.ChangeReadsetStatusButton;
import org.labkey.sequenceanalysis.button.ChangeReadsetStatusForAnalysesButton;
import org.labkey.sequenceanalysis.button.DownloadSraButton;
import org.labkey.sequenceanalysis.button.ReprocessLibraryButton;
import org.labkey.sequenceanalysis.button.RunMultiQCButton;
import org.labkey.sequenceanalysis.pipeline.*;
import org.labkey.sequenceanalysis.query.SequenceAnalysisUserSchema;
import org.labkey.sequenceanalysis.query.SequenceTriggerHelper;
import org.labkey.sequenceanalysis.run.RestoreSraDataHandler;
import org.labkey.sequenceanalysis.run.alignment.BWAMemWrapper;
import org.labkey.sequenceanalysis.run.alignment.BWASWWrapper;
import org.labkey.sequenceanalysis.run.alignment.BWAWrapper;
import org.labkey.sequenceanalysis.run.alignment.Bowtie2Wrapper;
import org.labkey.sequenceanalysis.run.alignment.BowtieWrapper;
import org.labkey.sequenceanalysis.run.alignment.GSnapWrapper;
import org.labkey.sequenceanalysis.run.alignment.MosaikWrapper;
import org.labkey.sequenceanalysis.run.alignment.Pbmm2Wrapper;
import org.labkey.sequenceanalysis.run.alignment.StarWrapper;
import org.labkey.sequenceanalysis.run.alignment.VulcanWrapper;
import org.labkey.sequenceanalysis.run.analysis.BamIterator;
import org.labkey.sequenceanalysis.run.analysis.BcftoolsFillTagsStep;
import org.labkey.sequenceanalysis.run.analysis.ExportOverlappingReadsAnalysis;
import org.labkey.sequenceanalysis.run.analysis.GenrichStep;
import org.labkey.sequenceanalysis.run.analysis.HaplotypeCallerAnalysis;
import org.labkey.sequenceanalysis.run.analysis.ImmunoGenotypingAnalysis;
import org.labkey.sequenceanalysis.run.analysis.LofreqAnalysis;
import org.labkey.sequenceanalysis.run.analysis.MergeLoFreqVcfHandler;
import org.labkey.sequenceanalysis.run.analysis.NextCladeHandler;
import org.labkey.sequenceanalysis.run.analysis.PARalyzerAnalysis;
import org.labkey.sequenceanalysis.run.analysis.PangolinHandler;
import org.labkey.sequenceanalysis.run.analysis.PbsvAnalysis;
import org.labkey.sequenceanalysis.run.analysis.PbsvJointCallingHandler;
import org.labkey.sequenceanalysis.run.analysis.PindelAnalysis;
import org.labkey.sequenceanalysis.run.analysis.SequenceBasedTypingAnalysis;
import org.labkey.sequenceanalysis.run.analysis.SnpCountAnalysis;
import org.labkey.sequenceanalysis.run.analysis.SubreadAnalysis;
import org.labkey.sequenceanalysis.run.analysis.UnmappedReadExportHandler;
import org.labkey.sequenceanalysis.run.analysis.ViralAnalysis;
import org.labkey.sequenceanalysis.run.assembly.TrinityRunner;
import org.labkey.sequenceanalysis.run.bampostprocessing.AddOrReplaceReadGroupsStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.BaseQualityScoreRecalibrator;
import org.labkey.sequenceanalysis.run.bampostprocessing.CallMdTagsStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.CleanSamStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.ClipOverlappingAlignmentsWrapper;
import org.labkey.sequenceanalysis.run.bampostprocessing.DiscardUnmappedReadsStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.FixMateInformationStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.IndelRealignerStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.LofreqIndelQualStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.MarkDuplicatesStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.MarkDuplicatesWithMateCigarStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.RnaSeQCStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.SortSamStep;
import org.labkey.sequenceanalysis.run.bampostprocessing.SplitNCigarReadsStep;
import org.labkey.sequenceanalysis.run.preprocessing.CutadaptCropWrapper;
import org.labkey.sequenceanalysis.run.preprocessing.CutadaptWrapper;
import org.labkey.sequenceanalysis.run.preprocessing.DownsampleFastqWrapper;
import org.labkey.sequenceanalysis.run.preprocessing.FastqcProcessingStep;
import org.labkey.sequenceanalysis.run.preprocessing.FilterReadsStep;
import org.labkey.sequenceanalysis.run.preprocessing.FlashPipelineStep;
import org.labkey.sequenceanalysis.run.preprocessing.PrintReadsContainingStep;
import org.labkey.sequenceanalysis.run.preprocessing.TagPcrSummaryStep;
import org.labkey.sequenceanalysis.run.preprocessing.TrimmomaticWrapper;
import org.labkey.sequenceanalysis.run.reference.CustomReferenceLibraryStep;
import org.labkey.sequenceanalysis.run.reference.DNAReferenceLibraryStep;
import org.labkey.sequenceanalysis.run.reference.SavedReferenceLibraryStep;
import org.labkey.sequenceanalysis.run.reference.VirusReferenceLibraryStep;
import org.labkey.sequenceanalysis.run.util.CombineGVCFsHandler;
import org.labkey.sequenceanalysis.run.util.FastqcRunner;
import org.labkey.sequenceanalysis.run.util.GenomicsDBAppendHandler;
import org.labkey.sequenceanalysis.run.util.GenomicsDBImportHandler;
import org.labkey.sequenceanalysis.run.variant.*;
import org.labkey.sequenceanalysis.util.Barcoder;
import org.labkey.sequenceanalysis.util.ChainFileValidator;
import org.labkey.sequenceanalysis.util.ScatterGatherUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class SequenceAnalysisModule extends ExtendedSimpleModule
{
    private static final Logger _log = LogHelper.getLogger(SequenceAnalysisModule.class, "Sequence Analysis Module Messages");

    public static final String NAME = "SequenceAnalysis";
    public static final String CONTROLLER_NAME = "sequenceanalysis";
    public static final String PROTOCOL = "Sequence Analysis";
    public static final ExperimentRunType EXP_RUN_TYPE = new SequenceAnalysisExperimentRunType();

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 12.329;
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
        addController(CONTROLLER_NAME, SequenceAnalysisController.class);
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        SequenceAnalysisManager sm = SequenceAnalysisManager.get();
        Collection<String> list = new LinkedList<>();

        int runCount = sm.getRunCount(c);
        int readsetCount = sm.getReadsetCount(c);

        if (readsetCount > 0)
            list.add(readsetCount + " Sequence Readsets");

        if (runCount > 0)
            list.add(runCount + " Sequence Analysis Runs");

        return list;
    }

    public static void registerPipelineSteps()
    {
        //pipelines
        try
        {
            AlignmentImportJob.register();
            SequenceAlignmentJob.register();
            ReadsetImportJob.register();
            IlluminaImportJob.register();
            AlignmentAnalysisJob.register();
        }
        catch (CloneNotSupportedException e)
        {
            _log.error(e.getMessage(), e);
        }

        //Step types:
        Arrays.stream(PipelineStep.CorePipelineStepTypes.values()).forEach(x -> {
            SequencePipelineService.get().registerPipelineStepType(x.getStepClass(), x.name());
        });

        //preprocessing
        SequencePipelineService.get().registerPipelineStep(new DownsampleFastqWrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new TrimmomaticWrapper.ReadLengthFilterProvider());
        SequencePipelineService.get().registerPipelineStep(new TrimmomaticWrapper.SlidingWindowTrimmingProvider());
        SequencePipelineService.get().registerPipelineStep(new TrimmomaticWrapper.CropReadsProvider());
        SequencePipelineService.get().registerPipelineStep(new FlashPipelineStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new PrintReadsContainingStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new FilterReadsStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new TrimmomaticWrapper.HeadCropReadsProvider());
        SequencePipelineService.get().registerPipelineStep(new TrimmomaticWrapper.MaxInfoTrimmingProvider());
        SequencePipelineService.get().registerPipelineStep(new TrimmomaticWrapper.AdapterTrimmingProvider());
        SequencePipelineService.get().registerPipelineStep(new TrimmomaticWrapper.AvgQualProvider());
        SequencePipelineService.get().registerPipelineStep(new TrimmomaticWrapper.LeadingTrimProvider());
        SequencePipelineService.get().registerPipelineStep(new TrimmomaticWrapper.TrailingTrimProvider());
        SequencePipelineService.get().registerPipelineStep(new CutadaptWrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new FastqcProcessingStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new CutadaptCropWrapper.Provider());
        //SequencePipelineService.get().registerPipelineStep(new BlastFilterPipelineStep.Provider());

        //ref library
        SequencePipelineService.get().registerPipelineStep(new DNAReferenceLibraryStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new VirusReferenceLibraryStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new CustomReferenceLibraryStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new SavedReferenceLibraryStep.Provider());

        //aligners
        SequencePipelineService.get().registerPipelineStep(new BowtieWrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new Bowtie2Wrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new BWAMemWrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new BWAWrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new BWASWWrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new MosaikWrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new GSnapWrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new StarWrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new Pbmm2Wrapper.Provider());
        SequencePipelineService.get().registerPipelineStep(new VulcanWrapper.Provider());

        //de novo assembly
        SequencePipelineService.get().registerPipelineStep(new TrinityRunner.Provider());

        //bam postprocessing
        SequencePipelineService.get().registerPipelineStep(new AddOrReplaceReadGroupsStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new CallMdTagsStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new CleanSamStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new FixMateInformationStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new IndelRealignerStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new BaseQualityScoreRecalibrator.BaseQualityScoreRecalibratorStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new DiscardUnmappedReadsStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new MarkDuplicatesStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new MarkDuplicatesWithMateCigarStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new SortSamStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new SplitNCigarReadsStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new ClipOverlappingAlignmentsWrapper.ClipOverlappingAlignmentsStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new LofreqIndelQualStep.Provider());

        //analysis
        SequencePipelineService.get().registerPipelineStep(new SequenceBasedTypingAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new ImmunoGenotypingAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new ViralAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new HaplotypeCallerAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new SnpCountAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new ExportOverlappingReadsAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new SubreadAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new TagPcrSummaryStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new LofreqAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new PindelAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new PbsvAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new GenrichStep.Provider());

        //SequencePipelineService.get().registerPipelineStep(new BlastUnmappedReadAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new PARalyzerAnalysis.Provider());
        SequencePipelineService.get().registerPipelineStep(new RnaSeQCStep.Provider());

        //variant processing
        SequencePipelineService.get().registerPipelineStep(new GenotypeFiltrationStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new SelectSNVsStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new SelectVariantsStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new SelectSamplesStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new SNPEffStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new VariantAnnotatorStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new VariantFiltrationStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new GenotypeConcordanceStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new SampleRenameStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new SplitVcfBySamplesStep.Provider());

        SequencePipelineService.get().registerPipelineStep(new VariantEvalStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new VariantEvalBySampleStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new VariantsToTableStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new VariantQCStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new PlinkPcaStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new KingInferenceStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new MendelianViolationReportStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new SummarizeGenotypeQualityStep.Provider());
        SequencePipelineService.get().registerPipelineStep(new BcftoolsFillTagsStep.Provider());

        //handlers
        SequenceAnalysisService.get().registerFileHandler(new LiftoverHandler());
        SequenceAnalysisService.get().registerFileHandler(new GenotypeGVCFHandler());
        SequenceAnalysisService.get().registerFileHandler(new CombineGVCFsHandler());
        //SequenceAnalysisService.get().registerFileHandler(new AlignmentMetricsHandler());
        SequenceAnalysisService.get().registerFileHandler(new UnmappedSequenceBasedGenotypeHandler());
        SequenceAnalysisService.get().registerFileHandler(new PicardAlignmentMetricsHandler());
        SequenceAnalysisService.get().registerFileHandler(new BamHaplotypeHandler());
        SequenceAnalysisService.get().registerFileHandler(new BamCleanupHandler());
        SequenceAnalysisService.get().registerFileHandler(new PrintReadBackedHaplotypesHandler());
        SequenceAnalysisService.get().registerFileHandler(new HaplotypeCallerHandler());
        SequenceAnalysisService.get().registerFileHandler(new ReblockGvcfHandler());
        SequenceAnalysisService.get().registerFileHandler(new RnaSeqcHandler());
        SequenceAnalysisService.get().registerFileHandler(new CombineStarGeneCountsHandler());
        SequenceAnalysisService.get().registerFileHandler(new CombineSubreadGeneCountsHandler());
        SequenceAnalysisService.get().registerFileHandler(new SbtGeneCountHandler());
        SequenceAnalysisService.get().registerFileHandler(new ProcessVariantsHandler());
        SequenceAnalysisService.get().registerFileHandler(new UnmappedReadExportHandler());
        SequenceAnalysisService.get().registerFileHandler(new MergeVcfsAndGenotypesHandler());
        SequenceAnalysisService.get().registerFileHandler(new DepthOfCoverageHandler());
        SequenceAnalysisService.get().registerFileHandler(new MultiAllelicPositionsHandler());
        SequenceAnalysisService.get().registerFileHandler(new RecalculateSequenceMetricsHandler());
        SequenceAnalysisService.get().registerFileHandler(new ListVcfSamplesHandler());
        SequenceAnalysisService.get().registerFileHandler(new MultiQCBamHandler());
        SequenceAnalysisService.get().registerFileHandler(new GenomicsDBImportHandler());
        SequenceAnalysisService.get().registerFileHandler(new GenomicsDBAppendHandler());
        SequenceAnalysisService.get().registerFileHandler(new MergeLoFreqVcfHandler());
        SequenceAnalysisService.get().registerFileHandler(new PangolinHandler());
        SequenceAnalysisService.get().registerFileHandler(new NextCladeHandler());
        SequenceAnalysisService.get().registerFileHandler(new ConvertToCramHandler());
        SequenceAnalysisService.get().registerFileHandler(new PbsvJointCallingHandler());

        SequenceAnalysisService.get().registerReadsetHandler(new MultiQCHandler());
        SequenceAnalysisService.get().registerReadsetHandler(new RestoreSraDataHandler());

        //ObjectFactory.Registry.register(AnalysisModelImpl.class, new UnderscoreBeanObjectFactory(AnalysisModelImpl.class));
        //ObjectFactory.Registry.register(SequenceReadsetImpl.class, new UnderscoreBeanObjectFactory(SequenceReadsetImpl.class));

        SequenceAnalysisService.get().registerGenomeTrigger(new CacheGenomeTrigger());
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(SequenceAnalysisSchema.SCHEMA_NAME);
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(SequenceAnalysisSchema.getInstance().getSchema());
    }

    @Override
    protected void registerSchemas()
    {
        SequenceAnalysisUserSchema.register(this);
    }

    @Override
    public void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        //NOTE: because this is not called on the remote server, startup tasks have been moved to the following:
        new PipelineStartup();

        LaboratoryService.get().registerDataProvider(new SequenceProvider(this));
        SequenceAnalysisService.get().registerDataProvider(new SequenceProvider(this));

        LDKService.get().registerQueryButton(new ShowEditUIButton(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_LIBRARIES, UpdatePermission.class), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
        LDKService.get().registerQueryButton(new ShowEditUIButton(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES, UpdatePermission.class), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
        LDKService.get().registerQueryButton(new ShowEditUIButton(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READ_DATA, UpdatePermission.class), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READ_DATA);
        LDKService.get().registerQueryButton(new ShowEditUIButton(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_SAVED_ANALYSES, UpdatePermission.class), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_SAVED_ANALYSES);
        LDKService.get().registerQueryButton(new ShowBulkEditButton(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);

        LDKService.get().registerQueryButton(new ShowBulkEditButton(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READ_DATA), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READ_DATA);

        LDKService.get().registerQueryButton(new AddSraRunButton(), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS);
        LDKService.get().registerQueryButton(new RunMultiQCButton(), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS);
        LDKService.get().registerQueryButton(new DownloadSraButton(), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS);

        LDKService.get().registerQueryButton(new ChangeReadsetStatusForAnalysesButton(), "sequenceanalysis", "sequence_analyses");
        LDKService.get().registerQueryButton(new ChangeReadsetStatusButton(), "sequenceanalysis", "sequence_readsets");

        ExperimentService.get().registerExperimentRunTypeSource(new ExperimentRunTypeSource()
        {
            @Override
            @NotNull
            public Set<ExperimentRunType> getExperimentRunTypes(@Nullable Container container)
            {
                if (container == null || container.getActiveModules().contains(SequenceAnalysisModule.this))
                {
                    return Collections.singleton(EXP_RUN_TYPE);
                }
                return Collections.emptySet();
            }
        });

        PipelineService.get().registerPipelineProvider(new ReferenceLibraryPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new SequenceOutputHandlerPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new SequenceReadsetHandlerPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new ImportFastaSequencesPipelineJob.Provider(this));
        PipelineService.get().registerPipelineProvider(new ImportGenomeTrackPipelineJob.Provider(this));
        PipelineService.get().registerPipelineProvider(new OrphanFilePipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new SequencePipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new CacheGenomePipelineJob.Provider(this));

        LDKService.get().registerQueryButton(new ReprocessLibraryButton(), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_LIBRARIES);

        SystemMaintenance.addTask(new SequenceAnalysisMaintenanceTask());

        ExperimentService.get().registerExperimentDataHandler(new HtmlExpDataHandler());

        SearchService ss = SearchService.get();

        if (null != ss)
            ss.addDocumentParser(new SequenceNoOpDocumentParser());
    }

    @Override
    protected void registerContainerListeners()
    {
        // add a container listener so we'll know when our container is deleted.  override the default to ensure correct order
        ContainerManager.addContainerListener(new SequenceAnalysisContainerListener(this));
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        @SuppressWarnings({"unchecked"})
        Set<Class> testClasses = new HashSet<Class>(Arrays.asList(
            Barcoder.TestCase.class,
            BamIterator.TestCase.class,
            SequenceIntegrationTests.SequenceImportPipelineTestCase.class,
            //SequenceIntegrationTests.SequenceAnalysisPipelineTestCase3.class,
            SequenceIntegrationTests.SequenceAnalysisPipelineTestCase1.class,
            SequenceIntegrationTests.SequenceAnalysisPipelineTestCase2.class,
            OutputIntegrationTests.VariantProcessingTest.class,
            SequenceRemoteIntegrationTests.class,
            SequenceTriggerHelper.TestCase.class
        ));

        return testClasses;
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return PageFlowUtil.set(
                SequenceAlignmentTask.TestCase.class,
                SequenceAnalysisManager.TestCase.class,
                SequenceJob.TestCase.class,
                SequenceJobSupportImpl.TestCase.class,
                ProcessVariantsHandler.TestCase.class,
                VariantProcessingJob.TestCase.class,
                ScatterGatherUtils.TestCase.class,
                ChainFileValidator.TestCase.class,
                FastqcRunner.TestCase.class
        );
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new SequenceAnalysisUpgradeCode();
    }
}