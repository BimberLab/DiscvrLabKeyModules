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
package org.labkey.sequenceanalysis.pipeline;

import htsjdk.samtools.BAMIndexer;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.ReadDataImpl;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.run.bampostprocessing.SortSamStep;
import org.labkey.sequenceanalysis.run.util.AddOrReplaceReadGroupsWrapper;
import org.labkey.sequenceanalysis.run.util.AlignmentSummaryMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.CollectInsertSizeMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.FastaIndexer;
import org.labkey.sequenceanalysis.run.util.FlagStatRunner;
import org.labkey.sequenceanalysis.run.util.MergeBamAlignmentWrapper;
import org.labkey.sequenceanalysis.run.util.MergeSamFilesWrapper;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This task is designed to act on either a FASTQ or zipped FASTQ file.  Each file should already have been imported as a readset using
 * the SequenceNormalizationTask pathway.  This task depends on external tools, and it will generall be configured to run on a remote server.
 *
 * Note: this task has been refactored to run as a split task, making the assumption that we run one per FASTQ (single-ended) or pair of files (paired-end)
 *
 */

public class SequenceAlignmentTask extends WorkDirectoryTask<SequenceAlignmentTask.Factory>
{
    private static final String PREPROCESS_FASTQ_STATUS = "PREPROCESSING FASTQ FILES";
    private static final String ALIGNMENT_STATUS = "PERFORMING ALIGNMENT";
    public static final String FINAL_BAM_ROLE = "Aligned Reads";
    public static final String FINAL_BAM_INDEX_ROLE = "Aligned Reads Index";

    //public static final String PREPROCESS_FASTQ_ACTIONNAME = "Preprocess FASTQ";
    public static final String ALIGNMENT_ACTIONNAME = "Performing Alignment";
    public static final String SORT_BAM_ACTION = "Sorting BAM";
    public static final String INDEX_ACTION = "Indexing BAM";
    public static final String RENAME_BAM_ACTION = "Renaming BAM";
    public static final String NORMALIZE_FILENAMES_ACTION = "Normalizing File Names";
    public static final String ALIGNMENT_METRICS_ACTIONNAME = "Calculating Alignment Metrics";

    public static final String MERGE_ALIGNMENT_ACTIONNAME = "Merging Alignments";
    public static final String DECOMPRESS_ACTIONNAME = "Decompressing Inputs";
    private static final String ALIGNMENT_SUBFOLDER_NAME = "Alignment";  //the subfolder within which alignment data will be placed
    private static final String COPY_REFERENCE_LIBRARY_ACTIONNAME = "Copy Reference";

    private SequenceTaskHelper _taskHelper;

    protected SequenceAlignmentTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(SequenceAlignmentTask.class);
            setJoin(true);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(FastqUtils.FqFileType);
        }

        @Override
        public String getStatusName()
        {
            return ALIGNMENT_STATUS;
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            List<String> allowableNames = new ArrayList<>();
            for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(PreprocessingStep.class))
            {
                allowableNames.add(provider.getLabel());
            }

            for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(BamProcessingStep.class))
            {
                allowableNames.add(provider.getLabel());
            }

            allowableNames.add(DECOMPRESS_ACTIONNAME);
            allowableNames.add(ALIGNMENT_ACTIONNAME);
            allowableNames.add(SORT_BAM_ACTION);
            allowableNames.add(INDEX_ACTION);
            allowableNames.add(RENAME_BAM_ACTION);
            allowableNames.add(COPY_REFERENCE_LIBRARY_ACTIONNAME);
            allowableNames.add(NORMALIZE_FILENAMES_ACTION);
            allowableNames.add(MERGE_ALIGNMENT_ACTIONNAME);
            allowableNames.add(ALIGNMENT_METRICS_ACTIONNAME);

            return allowableNames;
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SequenceAlignmentTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            FileAnalysisJobSupport support = (FileAnalysisJobSupport) job;
            String baseName = support.getBaseName();
            File dirAnalysis = support.getAnalysisDirectory();

            // job is complete when the output XML file exists
            return NetworkDrive.exists(getOutputXmlFile(dirAnalysis, baseName));
        }
    }

    public static File getOutputXmlFile(File dirAnalysis, String baseName)
    {
        FileType ft = new FileType(SequenceAnalysisManager.OUTPUT_XML_EXTENSION);
        String name = ft.getName(dirAnalysis, baseName);
        return new File(dirAnalysis, name);
    }

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        _taskHelper = new SequenceTaskHelper(getJob(), _wd);

        getJob().getLogger().info("Starting alignment");
        getHelper().logModuleVersions();
        List<RecordedAction> actions = new ArrayList<>();

        try
        {
            //then we preprocess FASTQ files, if needed
            List<SequenceReadsetImpl> readsets = getPipelineJob().getCachedReadsetModels();
            if (!readsets.isEmpty())
            {
                ReferenceGenome referenceGenome = getHelper().getSequenceSupport().getReferenceGenome();
                copyReferenceResources(actions);

                getJob().getLogger().debug("there are a total of " + readsets.size() + " readets passed to this task");
                if (getPipelineJob().getReadsetIdToProcesss() != null)
                {
                    getJob().getLogger().debug("readsets to process: " + getPipelineJob().getReadsetIdToProcesss().toString());
                }

                for (SequenceReadsetImpl rs : readsets)
                {
                    //NOTE: because these jobs can be split, we only process the readsets we chose to include
                    if (getPipelineJob().getReadsetIdToProcesss() != null && !getPipelineJob().getReadsetIdToProcesss().contains(rs.getReadsetId()))
                    {
                        continue;
                    }

                    String outputBasename = rs.getLegalFileName();
                    getJob().getLogger().info("starting readset: " + rs.getName());
                    getJob().getLogger().info("\ttotal file pairs: " + rs.getReadData().size());

                    Map<ReadDataImpl, Pair<File, File>> toAlign = new LinkedHashMap<>();
                    for (ReadDataImpl d : rs.getReadData())
                    {
                        getJob().getLogger().info("\tstarting files: " + d.getFile1().getName() + (d.getFile2() == null ? "" : " and " + d.getFile2().getName()));
                        Pair<File, File> pair = Pair.of(d.getFile1(), d.getFile2());

                        getJob().setStatus("DECOMPRESS INPUT FILES");
                        getHelper().getFileManager().decompressInputFiles(pair, actions);

                        pair = preprocessFastq(pair.first, pair.second, actions);
                        toAlign.put(d, pair);
                    }

                    if (SequenceTaskHelper.isAlignmentUsed(getJob()))
                    {
                        getJob().getLogger().info("Preparing to perform alignment");
                        alignSet(rs, outputBasename, toAlign, actions, referenceGenome);
                    }
                    else
                    {
                        getJob().getLogger().info("Alignment not selected, skipping");
                    }
                }

                //NOTE: we set this back to NULL, so subsequent steps will continue to use the local copy
                if (referenceGenome != null)
                {
                    referenceGenome.setWorkingFasta(null);
                }
            }
            else
            {
                throw new PipelineJobException("There are no readsets to align");
            }

            //remove unzipped files
            getJob().setStatus("PERFORMING CLEANUP");
            getHelper().getFileManager().processUnzippedInputs();
            getHelper().getFileManager().deleteIntermediateFiles();
            getHelper().getFileManager().cleanup();

            if (getHelper().getSettings().isDebugMode())
            {
                for (RecordedAction a : actions)
                {
                    getJob().getLogger().debug(a.getName() + "/" + a.getInputs().size() + "/" + a.getOutputs().size());
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return new RecordedActionSet(actions);
    }

    private SequenceAnalysisJob getPipelineJob()
    {
        return (SequenceAnalysisJob)getJob();
    }

    private void copyReferenceResources(List<RecordedAction> actions) throws PipelineJobException
    {
        if (!SequenceTaskHelper.isAlignmentUsed(getJob()))
        {
            return;
        }

        getJob().setStatus("COPYING REFERENCE LIBRARY");
        getJob().getLogger().info("copying reference library files");
        ReferenceGenome referenceGenome = getHelper().getSequenceSupport().getReferenceGenome();
        if (referenceGenome == null)
        {
            throw new PipelineJobException("No reference genome was cached prior to preparing aligned indexes");
        }

        File refFasta = referenceGenome.getSourceFastaFile();
        if (!refFasta.exists())
        {
            throw new PipelineJobException("Error: reference file does not exist: " + refFasta.getPath());
        }

        // TODO: assuming the aligner index step runs before this, we could always copy the product to the local working dir
        // this step would always push to the remote working dir.
        File fai = new File(refFasta.getPath() + ".fai");
        if (!fai.exists())
        {
            getJob().getLogger().info("creating FASTA Index");
            new FastaIndexer(getJob().getLogger()).execute(refFasta);
        }

        try
        {
            RecordedAction action = new RecordedAction(COPY_REFERENCE_LIBRARY_ACTIONNAME);
            action.setStartTime(new Date());

            String basename = FileUtil.getBaseName(refFasta);
            File targetDir = new File(_wd.getDir(), "Shared");
            for (File f : refFasta.getParentFile().listFiles())
            {
                if (f.getName().startsWith(basename))
                {
                    getJob().getLogger().debug("copying reference file: " + f.getPath());
                    File movedFile = _wd.inputFile(f, new File(targetDir, f.getName()), true);

                    action.addInput(f, "Reference File");
                    action.addOutput(movedFile, "Copied Reference File", true, true);
                }
            }

            action.setEndTime(new Date());
            actions.add(action);

            referenceGenome.setWorkingFasta(new File(targetDir, refFasta.getName()));
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private SequenceTaskHelper getHelper()
    {
        return _taskHelper;
    }

    /**
     * Attempt to normalize FASTQ files and perform preprocessing such as trimming.
     */
    public Pair<File, File> preprocessFastq(File inputFile1, @Nullable File inputFile2, List<RecordedAction> actions) throws PipelineJobException, IOException
    {
        getJob().setStatus(PREPROCESS_FASTQ_STATUS);
        getJob().getLogger().info("Beginning preprocessing for file: " + inputFile1.getName());
        if (inputFile2 != null)
        {
            getJob().getLogger().info("and file: " + inputFile2.getName());
        }

        //iterate over pipeline step
        List<PipelineStepProvider<PreprocessingStep>> providers = SequencePipelineService.get().getSteps(getJob(), PreprocessingStep.class);
        if (providers.isEmpty())
        {
            getJob().getLogger().info("No preprocessing is necessary");
            return Pair.of(inputFile1, inputFile2);
        }
        else
        {
            String originalbaseName = SequenceTaskHelper.getMinimalBaseName(inputFile1.getName());
            String originalbaseName2 = null;

            //log read count:
            Integer previousCount1 = FastqUtils.getSequenceCount(inputFile1);
            Integer previousCount2 = null;
            getJob().getLogger().info("\t" + inputFile1.getName() + ": " + previousCount1 + " sequences");

            if (inputFile2 != null)
            {
                previousCount2 = FastqUtils.getSequenceCount(inputFile2);
                getJob().getLogger().info("\t" + inputFile2.getName() + ": " + previousCount2 + " sequences");

                originalbaseName2 = SequenceTaskHelper.getMinimalBaseName(inputFile2.getName());
            }

            File outputDir = new File(getHelper().getWorkingDirectory(), originalbaseName);
            outputDir = new File(outputDir, SequenceTaskHelper.PREPROCESSING_SUBFOLDER_NAME);
            if (!outputDir.exists())
            {
                getJob().getLogger().debug("creating output directory: " + outputDir.getPath());
                outputDir.mkdirs();
            }

            Pair<File, File> pair = Pair.of(inputFile1, inputFile2);
            for (PipelineStepProvider<PreprocessingStep> provider : providers)
            {
                getJob().getLogger().info("***Preprocessing: " + provider.getLabel());

                RecordedAction action = new RecordedAction(provider.getLabel());
                action.setStartTime(new Date());
                getHelper().getFileManager().addInput(action, "Input FASTQ", pair.first);
                if (inputFile2 != null)
                {
                    getHelper().getFileManager().addInput(action, "Input FASTQ", pair.second);
                }

                PreprocessingStep step = provider.create(getHelper());
                getJob().setStatus("RUNNING: " + provider.getLabel().toUpperCase());
                PreprocessingStep.Output output = step.processInputFile(pair.first, pair.second, outputDir);
                getHelper().getFileManager().addStepOutputs(action, output);
                getHelper().getFileManager().addIntermediateFile(pair.first);
                if (pair.second != null)
                {
                    getHelper().getFileManager().addIntermediateFile(pair.second);
                }

                pair = output.getProcessedFastqFiles();
                if (output == null)
                {
                    throw new PipelineJobException("No FASTQ files found after preprocessing, aborting");
                }

                //log read count:
                int count1 = FastqUtils.getSequenceCount(pair.first);
                getJob().getLogger().info("\t" + pair.first.getName() + ": " + count1 + " sequences" + (previousCount1 != null ? ", difference from initial: " + (previousCount1 - count1) : ""));

                if (pair.second != null)
                {
                    int count2 = FastqUtils.getSequenceCount(pair.second);
                    getJob().getLogger().info("\t" + pair.second.getName() + ": " + count2 + " sequences" + (previousCount2 != null ? ", difference from initial: " + (previousCount2 - count2) : ""));
                }

                //TODO: read count / metrics

                action.setEndTime(new Date());
                actions.add(action);
            }

            //normalize name
            getJob().setStatus("NORMALIZING FILE NAME");
            RecordedAction action = new RecordedAction(NORMALIZE_FILENAMES_ACTION);
            action.setStartTime(new Date());
            File renamed1 = new File(pair.first.getParentFile(), originalbaseName + ".preprocessed.fastq");
            pair.first.renameTo(renamed1);
            getHelper().getFileManager().addInput(action, "Input", pair.first);
            getHelper().getFileManager().addOutput(action, "Output", renamed1);
            pair.first = renamed1;
            getHelper().getFileManager().addIntermediateFile(renamed1);

            getHelper().getFileManager().addIntermediateFile(pair.first);
            if (pair.second != null)
            {
                File renamed2 = new File(pair.second.getParentFile(), originalbaseName2 + ".preprocessed.fastq");
                pair.second.renameTo(renamed2);
                getHelper().getFileManager().addInput(action, "Input", pair.second);
                getHelper().getFileManager().addOutput(action, "Output", renamed2);
                getHelper().getFileManager().addIntermediateFile(pair.second);
                pair.second = renamed2;
                getHelper().getFileManager().addIntermediateFile(renamed2);
            }
            action.setEndTime(new Date());
            actions.add(action);

            return pair;
        }
    }

    private void alignSet(Readset rs, String basename, Map<ReadDataImpl, Pair<File, File>> files, List<RecordedAction> actions, ReferenceGenome referenceGenome) throws IOException, PipelineJobException
    {
        getJob().setStatus(ALIGNMENT_STATUS);
        getJob().getLogger().info("Beginning to align files for: " +  basename);

        List<AlignmentStep.AlignmentOutput> outputs = new ArrayList<>();
        int idx = 0;
        for (ReadDataImpl rd : files.keySet())
        {
            idx++;
            String msgSuffix = files.size() > 1 ? " (" + idx + " of " + files.size() + ")" : "";
            Pair<File, File> pair = files.get(rd);

            getJob().getLogger().info("Aligning inputs: " + pair.first.getName() + (pair.second == null ? "" : " and " + pair.second.getName()));

            outputs.add(doAlignmentForPair(actions, pair, referenceGenome, rs, rd, msgSuffix));
        }

        //merge outputs
        File bam;
        if (outputs.size() > 1)
        {
            getJob().setStatus("MERGING BAM FILES");
            getJob().getLogger().info("merging BAMs");
            RecordedAction mergeAction = new RecordedAction(MERGE_ALIGNMENT_ACTIONNAME);
            mergeAction.setStartTime(new Date());
            MergeSamFilesWrapper mergeSamFilesWrapper = new MergeSamFilesWrapper(getJob().getLogger());
            List<File> bams = new ArrayList<>();
            for (AlignmentStep.AlignmentOutput o : outputs)
            {
                bams.add(o.getBAM());
                getHelper().getFileManager().addInput(mergeAction, "Input BAM", o.getBAM());
            }

            bam = new File(outputs.get(0).getBAM().getParent(), FileUtil.getBaseName(outputs.get(0).getBAM().getName()) + ".merged.bam");
            getHelper().getFileManager().addOutput(mergeAction, "Merged BAM", bam);
            mergeSamFilesWrapper.execute(bams, bam.getPath(), true);

            mergeAction.setEndTime(new Date());
            actions.add(mergeAction);
        }
        else
        {
            bam = outputs.get(0).getBAM();
        }

        //post-processing
        List<PipelineStepProvider<BamProcessingStep>> providers = SequencePipelineService.get().getSteps(getJob(), BamProcessingStep.class);
        if (providers.isEmpty())
        {
            getJob().getLogger().info("No BAM postprocessing is necessary");
        }
        else
        {
            getJob().getLogger().info("***Starting BAM Post processing");
            getJob().setStatus("BAM POST-PROCESSING");
            getHelper().getFileManager().addIntermediateFile(bam);

            for (PipelineStepProvider<BamProcessingStep> provider : providers)
            {
                getJob().getLogger().info("performing step: " + provider.getLabel());
                getJob().setStatus("RUNNING: " + provider.getLabel().toUpperCase());
                getJob().getLogger().debug("BAM index exists: " + (new File(bam.getPath() + ".bai")).exists());

                RecordedAction action = new RecordedAction(provider.getLabel());
                action.setStartTime(new Date());
                getHelper().getFileManager().addInput(action, "Input BAM", bam);

                BamProcessingStep step = provider.create(getHelper());
                BamProcessingStep.Output output = step.processBam(rs, bam, referenceGenome, bam.getParentFile());
                getHelper().getFileManager().addStepOutputs(action, output);

                if (output.getBAM() != null)
                {
                    bam = output.getBAM();
                }
                getJob().getLogger().info("\ttotal alignments in processed BAM: " + SequenceUtil.getAlignmentCount(bam));

                getJob().getLogger().debug("index exists: " + (new File(bam.getPath() + ".bai")).exists());

                action.setEndTime(new Date());
                actions.add(action);
            }
        }

        //always end with coordinate sorted
        File finalBam;
        AlignmentStep alignmentStep = getHelper().getSingleStep(AlignmentStep.class).create(getHelper());
        if (alignmentStep.doSortIndexBam())
        {
            if (SequenceUtil.getBamSortOrder(bam) != SAMFileHeader.SortOrder.coordinate)
            {
                getJob().getLogger().info("***Sorting BAM: " + bam.getPath());
                getJob().setStatus("SORTING BAM");
                RecordedAction sortAction = new RecordedAction(SORT_BAM_ACTION);
                sortAction.setStartTime(new Date());
                getHelper().getFileManager().addInput(sortAction, "Input BAM", bam);

                SortSamStep sortStep = new SortSamStep.Provider().create(getHelper());
                BamProcessingStep.Output sortOutput = sortStep.processBam(rs, bam, referenceGenome, bam.getParentFile());
                getHelper().getFileManager().addStepOutputs(sortAction, sortOutput);
                getHelper().getFileManager().addIntermediateFile(bam);
                bam = sortOutput.getBAM();
                getHelper().getFileManager().addOutput(sortAction, "Sorted BAM", bam);

                sortAction.setEndTime(new Date());
                actions.add(sortAction);
            }
            else
            {
                getJob().getLogger().debug("BAM is already coordinate sorted, no need to sort");
            }

            //finally index
            getJob().setStatus("INDEXING BAM");
            RecordedAction indexAction = new RecordedAction(INDEX_ACTION);
            indexAction.setStartTime(new Date());
            getHelper().getFileManager().addInput(indexAction, "Input BAM", bam);
            File originalIndex = new File(bam.getPath() + ".bai");
            if (originalIndex.exists())
            {
                getJob().getLogger().debug("deleting existing BAM index: " + originalIndex.getName());
                originalIndex.delete();
            }

            finalBam = new File(bam.getParentFile(), basename + ".bam");
            if (!finalBam.getPath().equalsIgnoreCase(bam.getPath()))
            {
                getJob().getLogger().info("\tnormalizing BAM name to: " + finalBam.getPath());
                if (finalBam.exists())
                {
                    getJob().getLogger().warn("unexpected file, deleting: " + finalBam.getPath());
                }

                FileUtils.moveFile(bam, finalBam);
            }
            getHelper().getFileManager().addOutput(indexAction, FINAL_BAM_ROLE, finalBam);

            File bai = new File(finalBam.getPath() + ".bai");
            if (bai.exists())
            {
                getJob().getLogger().debug("deleting existing BAM index: " + bai.getName());
                bai.delete();
            }

            //TODO: SamReaderFactory fact = SamReaderFactory.make();
            //fact.validationStringency(ValidationStringency.SILENT);
            try (SAMFileReader reader = new SAMFileReader(finalBam))
            {
                getJob().getLogger().info("creating BAM index");
                reader.setValidationStringency(ValidationStringency.SILENT);
                BAMIndexer.createIndex(reader, bai);
                getHelper().getFileManager().addOutput(indexAction, FINAL_BAM_INDEX_ROLE, bai);
                getJob().getLogger().info("\tdone");
            }
            getJob().getLogger().info("\ttotal alignments in final BAM: " + SequenceUtil.getAlignmentCount(finalBam));

            indexAction.setEndTime(new Date());
            actions.add(indexAction);
        }
        else
        {
            getJob().setStatus("MOVING BAM");
            RecordedAction renameAction = new RecordedAction(RENAME_BAM_ACTION);
            renameAction.setStartTime(new Date());
            getHelper().getFileManager().addInput(renameAction, "Input BAM", bam);
            finalBam = new File(bam.getParentFile(), basename + ".bam");
            if (!finalBam.getPath().equalsIgnoreCase(bam.getPath()))
            {
                getJob().getLogger().info("\tnormalizing BAM name to: " + finalBam.getPath());
                if (finalBam.exists())
                {
                    getJob().getLogger().warn("unexpected file, deleting: " + finalBam.getPath());
                }

                FileUtils.moveFile(bam, finalBam);
            }
            getHelper().getFileManager().addOutput(renameAction, FINAL_BAM_ROLE, finalBam);

            renameAction.setEndTime(new Date());
            actions.add(renameAction);
        }

        //generate alignment metrics
        RecordedAction metricsAction = new RecordedAction(ALIGNMENT_METRICS_ACTIONNAME);
        metricsAction.setStartTime(new Date());
        getJob().getLogger().info("calculating alignment metrics");
        getJob().setStatus("CALCULATING ALIGNMENT SUMMARY METRICS");
        File metricsFile = new File(finalBam.getParentFile(), FileUtil.getBaseName(finalBam) + ".summary.metrics");
        new AlignmentSummaryMetricsWrapper(getJob().getLogger()).executeCommand(finalBam, metricsFile);
        getHelper().getFileManager().addInput(metricsAction, "BAM File", finalBam);
        getHelper().getFileManager().addOutput(metricsAction, "Summary Metrics File", metricsFile);

        //and insert size metrics
        if (rs.hasPairedData())
        {
            getJob().getLogger().info("calculating insert size metrics");
            getJob().setStatus("CALCULATING INSERT SIZE METRICS");
            File metricsFile2 = new File(finalBam.getParentFile(), FileUtil.getBaseName(finalBam) + ".insertsize.metrics");
            File metricsHistogram = new File(finalBam.getParentFile(), FileUtil.getBaseName(finalBam) + ".insertsize.metrics.pdf");
            if (new CollectInsertSizeMetricsWrapper(getJob().getLogger()).executeCommand(finalBam, metricsFile2, metricsHistogram) != null)
            {
                getHelper().getFileManager().addOutput(metricsAction, "Insert Size Metrics File", metricsFile2);
                getHelper().getFileManager().addOutput(metricsAction, "Insert Size  Metrics Histogram", metricsHistogram);
            }
        }

        metricsAction.setEndTime(new Date());
        actions.add(metricsAction);

        getJob().setStatus("PROCESSING BAM");

        //perform remote analyses, if necessary
        List<PipelineStepProvider<AnalysisStep>> analysisProviders = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
        if (analysisProviders.isEmpty())
        {
            getJob().getLogger().debug("no analyses were selected");
        }
        else
        {
            getJob().setStatus("PERFORMING ANALYSES ON ALIGNMENT");
            for (PipelineStepProvider<AnalysisStep> provider : analysisProviders)
            {
                getJob().setStatus("RUNNING: " + provider.getLabel().toUpperCase());

                getHelper().getJob().getLogger().info("Running analysis " + provider.getLabel() + " for readset: " + rs.getReadsetId());
                getHelper().getJob().getLogger().info("\tUsing alignment: " + finalBam.getPath());

                RecordedAction action = new RecordedAction(provider.getLabel());
                getHelper().getFileManager().addInput(action, "Input BAM File", finalBam);
                getHelper().getFileManager().addInput(action, "Reference DB FASTA", referenceGenome.getWorkingFastaFile());

                AnalysisStep step = provider.create(getHelper());
                AnalysisStep.Output o = step.performAnalysisPerSampleRemote(rs, finalBam, referenceGenome, finalBam.getParentFile());
                if (o != null)
                {
                    getHelper().getFileManager().addStepOutputs(action, o);
                }

                actions.add(action);
            }
        }
    }

    public AlignmentStep.AlignmentOutput doAlignmentForPair(List<RecordedAction> actions, Pair<File, File> inputFiles, ReferenceGenome referenceGenome, Readset rs, ReadData rd, String msgSuffix) throws PipelineJobException, IOException
    {
        //log input sequence count
        Integer count1 = FastqUtils.getSequenceCount(inputFiles.first);
        Integer count2 = inputFiles.second == null ? null : FastqUtils.getSequenceCount(inputFiles.second);

        getJob().getLogger().info("\t" + inputFiles.first.getName() + ": " + count1 + " sequences");
        if (inputFiles.second != null)
        {
            getJob().getLogger().info("\t" + inputFiles.second.getName() + ": " + count2 + " sequences");
        }

        RecordedAction alignmentAction = new RecordedAction(ALIGNMENT_ACTIONNAME);
        alignmentAction.setStartTime(new Date());
        getHelper().getFileManager().addInput(alignmentAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, inputFiles.first);

        if (inputFiles.second != null)
        {
            getHelper().getFileManager().addInput(alignmentAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, inputFiles.second);
        }

        getHelper().getFileManager().addInput(alignmentAction, ReferenceLibraryTask.REFERENCE_DB_FASTA, referenceGenome.getSourceFastaFile());
        getJob().getLogger().info("Beginning alignment for: " + inputFiles.first.getName() + (inputFiles.second == null ? "" : " and " + inputFiles.second.getName()) + msgSuffix);

        File outputDirectory = new File(getHelper().getWorkingDirectory(), SequenceTaskHelper.getMinimalBaseName(inputFiles.first.getName()));
        outputDirectory = new File(outputDirectory, ALIGNMENT_SUBFOLDER_NAME);
        if (!outputDirectory.exists())
        {
            getJob().getLogger().debug("creating directory: " + outputDirectory.getPath());
            outputDirectory.mkdirs();
        }

        AlignmentStep alignmentStep = getHelper().getSingleStep(AlignmentStep.class).create(getHelper());
        getJob().setStatus("RUNNING: " + alignmentStep.getProvider().getLabel().toUpperCase() + msgSuffix);
        AlignmentStep.AlignmentOutput alignmentOutput = alignmentStep.performAlignment(inputFiles.first, inputFiles.second, outputDirectory, referenceGenome, SequenceTaskHelper.getUnzippedBaseName(inputFiles.first.getName()) + "." + alignmentStep.getProvider().getName().toLowerCase());
        getHelper().getFileManager().addStepOutputs(alignmentAction, alignmentOutput);

        if (alignmentOutput.getBAM() == null || !alignmentOutput.getBAM().exists())
        {
            throw new PipelineJobException("Unable to find BAM file after alignment");
        }
        alignmentAction.setEndTime(new Date());
        actions.add(alignmentAction);

        SequenceUtil.logFastqBamDifferences(getJob().getLogger(), alignmentOutput.getBAM());

        ToolParameterDescriptor mergeParam = alignmentStep.getProvider().getParameterByName(AbstractAlignmentStepProvider.SUPPORT_MERGED_UNALIGNED);
        boolean doMergeUnaligned = mergeParam == null ? false : mergeParam.extractValue(getJob(), alignmentStep.getProvider(), Boolean.class, false);
        if (doMergeUnaligned)
        {
            getJob().setStatus("MERGING UNALIGNED READS INTO BAM" + (msgSuffix != null ? ": " + msgSuffix : ""));
            getJob().getLogger().info("merging unaligned reads into BAM");
            File idx = new File(alignmentOutput.getBAM().getPath() + ".bai");
            if (idx.exists())
            {
                getJob().getLogger().debug("deleting index: " + idx.getPath());
                idx.delete();
            }

            //merge unaligned reads and clean file
            new MergeBamAlignmentWrapper(getJob().getLogger()).executeCommand(referenceGenome.getWorkingFastaFile(), alignmentOutput.getBAM(), inputFiles.first, inputFiles.second, null);
        }
        else
        {
            getJob().getLogger().info("skipping merge of unaligned reads on BAM");
        }

        if (alignmentStep.doAddReadGroups())
        {
            getJob().setStatus("ADDING READ GROUPS" + (msgSuffix != null ? ": " + msgSuffix : ""));
            new AddOrReplaceReadGroupsWrapper(getJob().getLogger()).executeCommand(alignmentOutput.getBAM(), null, rs.getReadsetId().toString(), rs.getPlatform(), (rd.getPlatformUnit() == null ? rs.getReadsetId().toString() : rd.getPlatformUnit()), rs.getName().replaceAll(" ", "_"));
        }
        else
        {
            getJob().getLogger().info("skipping read group assignment");
        }

        //generate stats
        new FlagStatRunner(getJob().getLogger()).execute(alignmentOutput.getBAM());

        return alignmentOutput;
    }
}

