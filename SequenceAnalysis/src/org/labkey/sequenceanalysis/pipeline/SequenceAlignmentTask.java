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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.pipeline.ObjectKeySerialization;
import org.labkey.api.pipeline.PairSerializer;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractResumer;
import org.labkey.api.sequenceanalysis.pipeline.AbstractSequenceTaskFactory;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenomeManager;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.ReadDataImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.run.bampostprocessing.SortSamStep;
import org.labkey.sequenceanalysis.run.preprocessing.TrimmomaticWrapper;
import org.labkey.sequenceanalysis.run.util.AddOrReplaceReadGroupsWrapper;
import org.labkey.sequenceanalysis.run.util.AlignmentSummaryMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.BuildBamIndexWrapper;
import org.labkey.sequenceanalysis.run.util.CollectInsertSizeMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.CollectWgsMetricsWithNonZeroCoverageWrapper;
import org.labkey.sequenceanalysis.run.util.CollectWgsMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.FastaIndexer;
import org.labkey.sequenceanalysis.run.util.FlagStatRunner;
import org.labkey.sequenceanalysis.run.util.IdxStatsRunner;
import org.labkey.sequenceanalysis.run.util.MergeBamAlignmentWrapper;
import org.labkey.sequenceanalysis.run.util.MergeSamFilesWrapper;
import org.labkey.sequenceanalysis.util.FastqMerger;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static final String MERGE_FASTQ_ACTIONNAME = "Merging FASTQ Pairs";
    public static final String MERGE_ALIGNMENT_ACTIONNAME = "Merging Alignments";
    public static final String DECOMPRESS_ACTIONNAME = "Decompressing Inputs";
    private static final String ALIGNMENT_SUBFOLDER_NAME = "Alignment";  //the subfolder within which alignment data will be placed
    private static final String COPY_REFERENCE_LIBRARY_ACTIONNAME = "Copy Reference";
    private static final String COPY_INPUTS_ACTIONNAME = "Copying Inputs";

    private SequenceTaskHelper _taskHelper;
    private Resumer _resumer;

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
            allowableNames.add(COPY_INPUTS_ACTIONNAME);
            allowableNames.add(NORMALIZE_FILENAMES_ACTION);
            allowableNames.add(MERGE_ALIGNMENT_ACTIONNAME);
            allowableNames.add(MERGE_FASTQ_ACTIONNAME);
            allowableNames.add(ALIGNMENT_METRICS_ACTIONNAME);
            allowableNames.add(TrimmomaticWrapper.MultiStepTrimmomaticProvider.NAME);

            return allowableNames;
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SequenceAlignmentTask(this, job);
        }

        @Override
        public WorkDirectory createWorkDirectory(String jobGUID, FileAnalysisJobSupport jobSupport, Logger logger) throws IOException
        {
            WorkDirFactory factory = PipelineJobService.get().getWorkDirFactory();

            return factory.createWorkDirectory(jobGUID, jobSupport, true, logger);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            FileAnalysisJobSupport support = (FileAnalysisJobSupport) job;
            String baseName = support.getBaseName();
            File dirAnalysis = support.getAnalysisDirectory();

            return false;
        }
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        getJob().getLogger().info("Starting alignment");
        SequenceTaskHelper.logModuleVersions(getJob().getLogger());

        _taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);
        _resumer = Resumer.create(getPipelineJob(), this);
        if (_resumer.isResume())
        {
            getJob().getLogger().info("resuming previous job");
            if (_resumer.getFileManager() == null)
            {
                throw new PipelineJobException("fileManager is null for resumed job");
            }

            _taskHelper.setFileManager(_resumer.getFileManager());
        }

        try
        {
            SequenceReadsetImpl rs = getPipelineJob().getReadset();
            copyReferenceResources();

            //then we preprocess FASTQ files, if needed
            Map<ReadData, Pair<File, File>> toAlign = performFastqPreprocessing(rs);

            if (SequenceTaskHelper.isAlignmentUsed(getJob()))
            {
                getJob().setStatus(PipelineJob.TaskStatus.running, "PERFORMING ALIGNMENT");
                getJob().getLogger().info("Preparing to perform alignment");
                ReferenceGenome referenceGenome = getPipelineJob().getTargetGenome();
                String outputBasename = rs.getLegalFileName();
                alignSet(rs, outputBasename, toAlign, referenceGenome);
            }
            else
            {
                getJob().getLogger().info("Alignment not selected, skipping");
            }

            //NOTE: we set this back to NULL, so subsequent steps will continue to use the local copy
            if (getPipelineJob().getTargetGenome() != null)
            {
                getPipelineJob().getTargetGenome().setWorkingFasta(null);
            }

            //remove unzipped files
            getJob().setStatus(PipelineJob.TaskStatus.running, "PERFORMING CLEANUP");
            getHelper().getFileManager().processUnzippedInputs();
            getHelper().getFileManager().deleteIntermediateFiles();
            getHelper().getFileManager().deleteDeferredIntermediateFiles();
            getHelper().getFileManager().cleanup(_resumer.getRecordedActions(), _resumer);
            _resumer.markComplete(false);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return new RecordedActionSet(new RecordedActionSet(_resumer.getRecordedActions()));
    }

    private void logEnvironment() throws PipelineJobException
    {
        //list environment
        getJob().getLogger().debug("environment vars:");
        Map<String, String> env = System.getenv();
        for (String key : env.keySet())
        {
            getJob().getLogger().debug(key + " = " + env.get(key));
        }
    }

    private Map<ReadData, Pair<File, File>> performFastqPreprocessing(SequenceReadsetImpl rs) throws IOException, PipelineJobException
    {
        if (_resumer.isFastqPreprocessingDone())
        {
            getJob().getLogger().info("resuming FASTQ preprocessing from saved state");
            return _resumer.getFilesToAlign();
        }

        getJob().getLogger().info("starting readset: " + rs.getName());
        getJob().getLogger().info("\ttotal file pairs: " + rs.getReadData().size());
        List<RecordedAction> preprocessingActions = new ArrayList<>();
        Map<File, File> copiedInputs = new HashMap<>();

        Map<ReadData, Pair<File, File>> toAlign = new LinkedHashMap<>();
        int i = 0;
        Set<String> basenamesUsed = new CaseInsensitiveHashSet();
        for (ReadData d : rs.getReadData())
        {
            i++;
            String suffix = " (set " + i + " of " + rs.getReadData().size() + ")";

            getJob().getLogger().info("\tstarting files: " + d.getFile1().getName() + (d.getFile2() == null ? "" : " and " + d.getFile2().getName()));
            Pair<File, File> pair = Pair.of(d.getFile1(), d.getFile2());

            //copy to work dir
            if (getHelper().getFileManager().isCopyInputsLocally())
            {
                getJob().getLogger().info("copying files to work directory");
                getJob().setStatus(PipelineJob.TaskStatus.running, "Copying Files to Work Directory: " + suffix);

                RecordedAction copyAction = new RecordedAction(COPY_INPUTS_ACTIONNAME);
                preprocessingActions.add(copyAction);
                File target = new File(getHelper().getWorkingDirectory(), pair.first.getName());

                target = _wd.inputFile(pair.first, target, false);
                copiedInputs.put(pair.first, target);
                getHelper().getFileManager().addInput(copyAction, "Input FASTQ", pair.first);
                getHelper().getFileManager().addOutput(copyAction, "Copied FASTQ", target);
                pair.first = target;

                if (pair.second != null)
                {
                    File target2 = new File(getHelper().getWorkingDirectory(), pair.second.getName());
                    target2 = _wd.inputFile(pair.second, target2, false);
                    copiedInputs.put(pair.second, target2);
                    getHelper().getFileManager().addInput(copyAction, "Input FASTQ", pair.second);
                    getHelper().getFileManager().addOutput(copyAction, "Copied FASTQ", target2);
                    pair.second = target2;
                }
                getJob().getLogger().info("copy done");
            }
            else
            {
                getJob().getLogger().debug("Will not copy inputs to working directory");
            }

            pair = preprocessFastq(pair.first, pair.second, preprocessingActions, suffix, basenamesUsed);
            toAlign.put(d, pair);
        }

        _resumer.setFastqPreprocessingDone(toAlign, preprocessingActions, copiedInputs);

        return toAlign;
    }

    private SequenceAlignmentJob getPipelineJob()
    {
        return (SequenceAlignmentJob)getJob();
    }

    private void copyReferenceResources() throws PipelineJobException
    {
        if (!SequenceTaskHelper.isAlignmentUsed(getJob()))
        {
            return;
        }

        ReferenceGenome referenceGenome = getPipelineJob().getTargetGenome();
        if (referenceGenome == null)
        {
            throw new PipelineJobException("No reference genome was cached prior to preparing aligned indexes");
        }

        if (_resumer.hasCopiedResources())
        {
            getJob().getLogger().info("copy reference resources already performed, skipping");
            if (!referenceGenome.getSourceFastaFile().equals(_resumer.getWorkingFasta()))
            {
                referenceGenome.setWorkingFasta(_resumer.getWorkingFasta());
            }

            return;
        }

        List<RecordedAction> actions = new ArrayList<>();
        Map<File, File> copiedInputs = new HashMap<>();

        getJob().setStatus(PipelineJob.TaskStatus.running, "COPYING REFERENCE LIBRARY");
        getJob().getLogger().info("Potentially copying reference library files");

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

        //check client-supplied params
        String val = getJob().getParameters().get(AlignerIndexUtil.COPY_LOCALLY);
        boolean doCopy = val == null ? true : ConvertHelper.convert(val, Boolean.class);

        //but let aligners override this.
        AlignmentStep alignmentStep = getHelper().getSingleStep(AlignmentStep.class).create(getHelper());
        if (alignmentStep.alwaysCopyIndexToWorkingDir())
        {
            getJob().getLogger().info("The selected aligner requires a local copy of the FASTA, so it will be copied");
            doCopy = true;
        }

        if (SequencePipelineService.get().isRemoteGenomeCacheUsed())
        {
            ReferenceGenomeManager.get().cacheGenomeLocally(referenceGenome, getJob().getLogger());
        }
        else if (doCopy)
        {
            try
            {
                RecordedAction action = new RecordedAction(COPY_REFERENCE_LIBRARY_ACTIONNAME);
                Date start = new Date();
                action.setStartTime(start);

                String basename = FileUtil.getBaseName(refFasta);
                File targetDir = new File(_wd.getDir(), "Shared");
                for (File f : refFasta.getParentFile().listFiles())
                {
                    if (f.getName().startsWith(basename))
                    {
                        getJob().getLogger().debug("copying reference file: " + f.getPath());
                        File movedFile = _wd.inputFile(f, new File(targetDir, f.getName()), true);
                        copiedInputs.put(f, movedFile);
                        action.addInputIfNotPresent(f, "Reference File");
                        action.addOutputIfNotPresent(movedFile, "Copied Reference File", true);
                    }
                }

                Date end = new Date();
                action.setEndTime(end);
                getJob().getLogger().info("Copy Reference Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
                actions.add(action);

                referenceGenome.setWorkingFasta(new File(targetDir, refFasta.getName()));
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            getJob().getLogger().debug("Will not copy reference library locally");
        }

        _resumer.setHasCopiedResources(referenceGenome.getWorkingFastaFile(), actions, copiedInputs);
    }

    private SequenceTaskHelper getHelper()
    {
        return _taskHelper;
    }

    /**
     * Attempt to normalize FASTQ files and perform preprocessing such as trimming.
     */
    public Pair<File, File> preprocessFastq(File inputFile1, @Nullable File inputFile2, List<RecordedAction> actions, String statusSuffix, Set<String> basenamesUsed) throws PipelineJobException, IOException
    {
        getJob().setStatus(PipelineJob.TaskStatus.running, PREPROCESS_FASTQ_STATUS);
        getJob().getLogger().info("Beginning preprocessing for file: " + inputFile1.getName());
        if (inputFile2 != null)
        {
            getJob().getLogger().info("and file: " + inputFile2.getName());
        }

        //iterate over pipeline step
        List<PipelineStepCtx<PreprocessingStep>> steps = SequencePipelineService.get().getSteps(getJob(), PreprocessingStep.class);
        if (steps.isEmpty())
        {
            getJob().getLogger().info("No preprocessing is necessary");
            return Pair.of(inputFile1, inputFile2);
        }
        else
        {
            //combine providers as needed
            List<PipelineStepCtx<PreprocessingStep>> combinedSteps = new ArrayList<>();
            combinedSteps.add(steps.remove(0));
            while (!steps.isEmpty())
            {
                PipelineStepCtx<PreprocessingStep> previous = combinedSteps.get(combinedSteps.size() - 1);
                PipelineStepProvider<PreprocessingStep> combinedStepProvider = previous.getProvider().combineSteps(previous.getStepIdx(), steps.get(0));
                if (combinedStepProvider != null && previous.getStepIdx() == 0 && steps.get(0).getStepIdx() == 0)
                {
                    //add this combined step to replace the previous
                    getJob().getLogger().debug("combining step: " + previous.getProvider().getLabel() + " with " + steps.get(0).getProvider().getLabel());
                    combinedSteps.remove(combinedSteps.size() - 1);
                    combinedSteps.add(new PipelineStepCtxImpl<>(combinedStepProvider, 0));
                }
                else
                {
                    //otherwise append to the new list and remove from this list
                    combinedSteps.add(steps.get(0));
                }

                steps.remove(0);
            }

            steps = combinedSteps;

            String originalbaseName = SequenceTaskHelper.getMinimalBaseName(inputFile1.getName());
            int i = 0;
            while (basenamesUsed.contains(originalbaseName))
            {
                i++;
                originalbaseName = originalbaseName + "." + i;
            }
            basenamesUsed.add(originalbaseName);
            String originalbaseName2 = null;

            //log read count:
            Pair<Long, Long> previousCounts = FastqUtils.logSequenceCounts(inputFile1, inputFile2, getJob().getLogger(), null, null);

            if (inputFile2 != null)
            {
                originalbaseName2 = SequenceTaskHelper.getUnzippedBaseName(inputFile2.getName());
                i = 0;
                while (basenamesUsed.contains(originalbaseName2))
                {
                    i++;
                    originalbaseName2 = originalbaseName2 + "." + i;
                }
                basenamesUsed.add(originalbaseName2);

                if (originalbaseName.equalsIgnoreCase(originalbaseName2))
                {
                    getJob().getLogger().debug("Forward and reverse FASTQs have the same basename.  Appending .R1 and .R2 as suffixes.");
                    originalbaseName = originalbaseName + ".R1";
                    originalbaseName2 = originalbaseName2 + ".R2";
                }
            }

            File outputDir = new File(getHelper().getWorkingDirectory(), originalbaseName);
            outputDir = new File(outputDir, SequenceTaskHelper.PREPROCESSING_SUBFOLDER_NAME);
            if (!outputDir.exists())
            {
                getJob().getLogger().debug("creating output directory: " + outputDir.getPath());
                outputDir.mkdirs();
            }

            Pair<File, File> pair = Pair.of(inputFile1, inputFile2);
            for (PipelineStepCtx<PreprocessingStep> stepCtx : steps)
            {
                getJob().getLogger().info("***Preprocessing: " + stepCtx.getProvider().getLabel());

                RecordedAction action = new RecordedAction(stepCtx.getProvider().getLabel());
                Date start = new Date();
                action.setStartTime(start);
                getHelper().getFileManager().addInput(action, "Input FASTQ", pair.first);
                if (pair.second != null)
                {
                    getHelper().getFileManager().addInput(action, "Input FASTQ", pair.second);
                }

                PreprocessingStep step = stepCtx.getProvider().create(getHelper());
                step.setStepIdx(stepCtx.getStepIdx());
                getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING: " + step.getProvider().getLabel().toUpperCase() + statusSuffix);
                PreprocessingStep.Output output = step.processInputFile(pair.first, pair.second, outputDir);
                getJob().getLogger().debug("\tstep complete");
                if (output == null)
                {
                    throw new PipelineJobException("No FASTQ files found after preprocessing, aborting");
                }

                pair = output.getProcessedFastqFiles();
                if (pair == null)
                {
                    throw new PipelineJobException("No FASTQ files found after preprocessing, aborting");
                }
                else if (pair.second != null && pair.first.equals(pair.second))
                {
                    throw new PipelineJobException("First and second FASTQs are identical files.  This can occur is the basename of the forward and reverse files are the same");
                }

                getHelper().getFileManager().addStepOutputs(action, output);
                getHelper().getFileManager().addIntermediateFile(pair.first);

                if (pair.second != null)
                {
                    getHelper().getFileManager().addIntermediateFile(pair.second);
                }

                //log read count:
                FastqUtils.logSequenceCounts(pair.first, pair.second, getJob().getLogger(), previousCounts.first, previousCounts.second);

                //TODO: read count / metrics
                //getHelper().getFileManager()

                Date end = new Date();
                action.setEndTime(end);
                getJob().getLogger().info(stepCtx.getProvider().getLabel() + " Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
                actions.add(action);
            }

            //normalize name
            getJob().setStatus(PipelineJob.TaskStatus.running, "NORMALIZING FILE NAME");
            String extension = FileUtil.getExtension(pair.first).endsWith("gz") ? ".fastq.gz" : ".fastq";
            RecordedAction action = new RecordedAction(NORMALIZE_FILENAMES_ACTION);
            Date start = new Date();
            action.setStartTime(start);
            File renamed1 = new File(pair.first.getParentFile(), originalbaseName + ".preprocessed" + extension);
            if (renamed1.exists())
            {
                getJob().getLogger().debug("deleting existing file prior to rename: " + renamed1.getName());
                renamed1.delete();
            }
            pair.first.renameTo(renamed1);
            getJob().getLogger().info("normalizing file name to: " + renamed1.getName());
            getHelper().getFileManager().addInput(action, "Input", pair.first);
            getHelper().getFileManager().addOutput(action, "Output", renamed1);
            pair.first = renamed1;
            getHelper().getFileManager().addIntermediateFile(renamed1);
            getHelper().getFileManager().addIntermediateFile(pair.first);

            if (pair.second != null)
            {
                File renamed2 = new File(pair.second.getParentFile(), originalbaseName2 + ".preprocessed" + extension);
                if (renamed2.exists())
                {
                    getJob().getLogger().debug("deleting existing file prior to rename: " + renamed2.getName());
                    renamed2.delete();
                }
                pair.second.renameTo(renamed2);
                getJob().getLogger().info("and: " + renamed2.getName());
                getHelper().getFileManager().addInput(action, "Input", pair.second);
                getHelper().getFileManager().addOutput(action, "Output", renamed2);
                getHelper().getFileManager().addIntermediateFile(pair.second);
                pair.second = renamed2;
                getHelper().getFileManager().addIntermediateFile(renamed2);
            }
            Date end = new Date();
            action.setEndTime(end);
            getJob().getLogger().info("Normalize Filename Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
            actions.add(action);

            return pair;
        }
    }

    private void alignSet(Readset rs, String basename, Map<ReadData, Pair<File, File>> files, ReferenceGenome referenceGenome) throws IOException, PipelineJobException
    {
        File bam;
        if (_resumer.isInitialAlignmentDone())
        {
            getJob().getLogger().info("resuming initial alignment from saved state");
            bam = _resumer.getMergedBamFile();
            getJob().getLogger().debug("file: " + bam.getPath());
        }
        else
        {
            getJob().setStatus(PipelineJob.TaskStatus.running, ALIGNMENT_STATUS);
            getJob().getLogger().info("Beginning to align files for: " + basename);

            List<RecordedAction> alignActions = new ArrayList<>();
            bam = doAlignment(referenceGenome, rs, files, alignActions);

            _resumer.setInitialAlignmentDone(bam, alignActions);
        }

        //post-processing
        if (_resumer.isBamPostProcessingBamDone())
        {
            getJob().getLogger().info("resuming BAM post processing from saved state");
            bam = _resumer.getPostProcessedBamFile();
            getJob().getLogger().debug("file: " + bam.getPath());
        }
        else
        {
            List<RecordedAction> postProcessActions = new ArrayList<>();
            List<PipelineStepCtx<BamProcessingStep>> steps = SequencePipelineService.get().getSteps(getJob(), BamProcessingStep.class);
            if (steps.isEmpty())
            {
                getJob().getLogger().info("No BAM postprocessing is necessary");
            }
            else
            {
                getJob().getLogger().info("***Starting BAM Post processing");
                getJob().setStatus(PipelineJob.TaskStatus.running, "BAM POST-PROCESSING");
                getHelper().getFileManager().addIntermediateFile(bam);
                File idx = new File(bam.getPath() + ".bai");
                if (idx.exists())
                {
                    getHelper().getFileManager().addIntermediateFile(idx);
                }

                for (PipelineStepCtx<BamProcessingStep> stepCtx : steps)
                {
                    getJob().getLogger().info("performing step: " + stepCtx.getProvider().getLabel());
                    getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING: " + stepCtx.getProvider().getLabel().toUpperCase());
                    getJob().getLogger().debug("BAM index exists: " + (new File(bam.getPath() + ".bai")).exists());

                    RecordedAction action = new RecordedAction(stepCtx.getProvider().getLabel());
                    Date start = new Date();
                    action.setStartTime(start);
                    getHelper().getFileManager().addInput(action, "Input BAM", bam);

                    BamProcessingStep step = stepCtx.getProvider().create(getHelper());
                    step.setStepIdx(stepCtx.getStepIdx());
                    BamProcessingStep.Output output = step.processBam(rs, bam, referenceGenome, bam.getParentFile());
                    getHelper().getFileManager().addStepOutputs(action, output);

                    if (output.getBAM() != null)
                    {
                        //If we have change the BAM, mark the previous for deletion
                        if (!bam.equals(output.getBAM()))
                        {
                            getHelper().getFileManager().addIntermediateFile(bam);
                            getHelper().getFileManager().addIntermediateFile(new File(bam.getPath() + ".bai"));
                        }

                        bam = output.getBAM();

                        //can take a long time to execute
                        //getJob().getLogger().info("\ttotal alignments in processed BAM: " + SequenceUtil.getAlignmentCount(bam));
                        getJob().getLogger().info("\tfile size: " + FileUtils.byteCountToDisplaySize(bam.length()));
                    }
                    else if (step.expectToCreateNewBam())
                    {
                        throw new PipelineJobException("The BAM processing step should have created a new BAM, no BAM was specified. This is possible a coding error on this step");
                    }
                    else
                    {
                        getJob().getLogger().info("no BAM created by step, using output from previous step");
                    }

                    getJob().getLogger().debug("index exists: " + (new File(bam.getPath() + ".bai")).exists());

                    Date end = new Date();
                    action.setEndTime(end);
                    getJob().getLogger().info(stepCtx.getProvider().getLabel() + " Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
                    postProcessActions.add(action);
                }
            }

            _resumer.setBamPostProcessingBamDone(bam, postProcessActions);
        }

        //always end with coordinate sorted
        getJob().setStatus(PipelineJob.TaskStatus.running, "SORTING BAM");
        AlignmentStep alignmentStep = getHelper().getSingleStep(AlignmentStep.class).create(getHelper());
        if (_resumer.isBamSortDone())
        {
            getJob().getLogger().info("BAM sort already performed, resuming");
            bam = _resumer.getSortedBamFile();
            getJob().getLogger().info("file: " + bam.getPath());
        }
        else
        {
            RecordedAction sortAction = null;
            if (alignmentStep.doSortIndexBam())
            {
                if (SequenceUtil.getBamSortOrder(bam) != SAMFileHeader.SortOrder.coordinate)
                {
                    getJob().getLogger().info("***Sorting BAM: " + bam.getPath());
                    getJob().setStatus(PipelineJob.TaskStatus.running, "SORTING BAM");

                    Date start = new Date();
                    sortAction = new RecordedAction(SORT_BAM_ACTION);
                    sortAction.setStartTime(start);
                    getHelper().getFileManager().addInput(sortAction, "Input BAM", bam);

                    //TODO: consider samtools instead?
                    SortSamStep sortStep = new SortSamStep.Provider().create(getHelper());
                    BamProcessingStep.Output sortOutput = sortStep.processBam(rs, bam, referenceGenome, bam.getParentFile());
                    getHelper().getFileManager().addStepOutputs(sortAction, sortOutput);
                    getHelper().getFileManager().addIntermediateFile(bam);
                    bam = sortOutput.getBAM();
                    getHelper().getFileManager().addOutput(sortAction, "Sorted BAM", bam);
                    getHelper().getFileManager().addCommandsToAction(sortOutput.getCommandsExecuted(), sortAction);

                    Date end = new Date();
                    sortAction.setEndTime(end);
                    getJob().getLogger().info("SortBam Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
                }
                else
                {
                    getJob().getLogger().debug("BAM is already coordinate sorted, no need to sort");
                }
            }

            _resumer.setBamSortDone(bam, sortAction);
        }

        File renamedBam;
        getJob().setStatus(PipelineJob.TaskStatus.running, "RENAMING BAM");
        if (_resumer.isBamRenameDone())
        {
            getJob().getLogger().info("BAM rename already performed, resuming");
            renamedBam = _resumer.getRenamedBamFile();
        }
        else
        {
            getJob().setStatus(PipelineJob.TaskStatus.running, "MOVING BAM");
            RecordedAction renameAction = new RecordedAction(RENAME_BAM_ACTION);
            Date start = new Date();
            renameAction.setStartTime(start);
            getHelper().getFileManager().addInput(renameAction, "Input BAM", bam);
            renamedBam = new File(bam.getParentFile(), basename + ".bam");
            if (!renamedBam.getPath().equalsIgnoreCase(bam.getPath()))
            {
                getJob().getLogger().info("\tnormalizing BAM name to: " + renamedBam.getPath());
                if (renamedBam.exists())
                {
                    getJob().getLogger().warn("unexpected file, deleting: " + renamedBam.getPath());
                    renamedBam.delete();
                }
                FileUtils.moveFile(bam, renamedBam);

                File bamIdxOrig = new File(bam.getPath() + ".bai");
                File finalBamIdx = new File(renamedBam.getPath() + ".bai");
                if (finalBamIdx.exists())
                {
                    getJob().getLogger().warn("unexpected file, deleting: " + finalBamIdx.getPath());
                    finalBamIdx.delete();
                }

                if (bamIdxOrig.exists())
                {
                    getJob().getLogger().debug("also moving bam index");


                    FileUtils.moveFile(bamIdxOrig, finalBamIdx);
                }
            }
            getHelper().getFileManager().addOutput(renameAction, FINAL_BAM_ROLE, renamedBam);

            Date end = new Date();
            renameAction.setEndTime(end);
            getJob().getLogger().info("Rename Bam Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));

            _resumer.setBamRenameDone(renamedBam, Arrays.asList(renameAction));
        }

        getJob().setStatus(PipelineJob.TaskStatus.running, "INDEXING BAM");
        if (_resumer.isIndexBamDone())
        {
            getJob().getLogger().info("BAM indexing already performed");
        }
        else
        {
            List<RecordedAction> bamActions = new ArrayList<>();
            if (alignmentStep.doSortIndexBam())
            {
                //finally index
                getJob().setStatus(PipelineJob.TaskStatus.running, "INDEXING BAM");
                RecordedAction indexAction = new RecordedAction(INDEX_ACTION);
                Date start = new Date();
                indexAction.setStartTime(start);
                getHelper().getFileManager().addInput(indexAction, "Input BAM", bam);
                File originalIndex = new File(bam.getPath() + ".bai");
                if (originalIndex.exists())
                {
                    getJob().getLogger().debug("deleting existing BAM index: " + originalIndex.getName());
                    originalIndex.delete();
                }

                renamedBam = new File(bam.getParentFile(), basename + ".bam");
                getHelper().getFileManager().addInput(indexAction, FINAL_BAM_ROLE, renamedBam);

                File bai = new File(renamedBam.getPath() + ".bai");
                if (bai.exists())
                {
                    getJob().getLogger().debug("deleting existing BAM index: " + bai.getName());
                    bai.delete();
                }

                BuildBamIndexWrapper buildBamIndexWrapper = new BuildBamIndexWrapper(getJob().getLogger());
                buildBamIndexWrapper.setStringency(ValidationStringency.SILENT);
                buildBamIndexWrapper.executeCommand(renamedBam);
                getHelper().getFileManager().addOutput(indexAction, FINAL_BAM_INDEX_ROLE, bai);
                getHelper().getFileManager().addCommandsToAction(buildBamIndexWrapper.getCommandsExecuted(), indexAction);

                getJob().getLogger().info("\tfile size: " + FileUtils.byteCountToDisplaySize(bam.length()));

                Date end = new Date();
                indexAction.setEndTime(end);
                getJob().getLogger().info("IndexBam Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
                bamActions.add(indexAction);

                _resumer.setIndexBamDone(true, indexAction);
            }
        }

        getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING BAM METRICS");
        if (_resumer.isAlignmentMetricsDone())
        {
            getJob().getLogger().info("alignment metrics step has already been completed, will not repeat");
        }
        else
        {
            //generate alignment metrics
            RecordedAction metricsAction = null;
            boolean supportsMetrics = alignmentStep.supportsMetrics();
            SAMFileHeader.SortOrder so = SequencePipelineService.get().getBamSortOrder(renamedBam);
            File index = new File(renamedBam.getPath() + ".bai");
            if (!supportsMetrics)
            {
                getPipelineJob().getLogger().debug("this aligner does not support collection of alignment metrics");
            }
            else
            {
                if (so == SAMFileHeader.SortOrder.coordinate && index.exists())
                {
                    metricsAction = new RecordedAction(ALIGNMENT_METRICS_ACTIONNAME);

                    Date start = new Date();
                    metricsAction.setStartTime(start);
                    getJob().getLogger().info("calculating alignment metrics");
                    getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING ALIGNMENT SUMMARY METRICS");
                    File metricsFile = new File(renamedBam.getParentFile(), FileUtil.getBaseName(renamedBam) + ".summary.metrics");
                    AlignmentSummaryMetricsWrapper wrapper = new AlignmentSummaryMetricsWrapper(getJob().getLogger());
                    wrapper.executeCommand(renamedBam, referenceGenome.getWorkingFastaFile(), metricsFile);
                    getHelper().getFileManager().addInput(metricsAction, "BAM File", renamedBam);
                    getHelper().getFileManager().addOutput(metricsAction, "Summary Metrics File", metricsFile);
                    getHelper().getFileManager().addPicardMetricsFiles(Arrays.asList(new PipelineStepOutput.PicardMetricsOutput(metricsFile, renamedBam, rs.getRowId())));
                    getHelper().getFileManager().addCommandsToAction(wrapper.getCommandsExecuted(), metricsAction);

                    //wgs metrics
                    ToolParameterDescriptor wgsParam = alignmentStep.getProvider().getParameterByName(AbstractAlignmentStepProvider.COLLECT_WGS_METRICS);
                    boolean doCollectWgsMetrics = wgsParam == null ? false : wgsParam.extractValue(getJob(), alignmentStep.getProvider(), alignmentStep.getStepIdx(), Boolean.class, false);

                    if (doCollectWgsMetrics)
                    {
                        getJob().getLogger().info("calculating wgs metrics");
                        getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING WGS METRICS");
                        File wgsMetricsFile = new File(renamedBam.getParentFile(), FileUtil.getBaseName(renamedBam) + ".wgs.metrics");
                        CollectWgsMetricsWrapper wgsWrapper = new CollectWgsMetricsWrapper(getJob().getLogger());
                        wgsWrapper.executeCommand(renamedBam, wgsMetricsFile, referenceGenome.getWorkingFastaFile());
                        getHelper().getFileManager().addOutput(metricsAction, "WGS Metrics File", wgsMetricsFile);
                        getHelper().getFileManager().addPicardMetricsFiles(Arrays.asList(new PipelineStepOutput.PicardMetricsOutput(wgsMetricsFile, renamedBam, rs.getRowId())));
                        getHelper().getFileManager().addCommandsToAction(wgsWrapper.getCommandsExecuted(), metricsAction);
                    }

                    //non-zero wgs metrics
                    ToolParameterDescriptor wgsParamNonZero = alignmentStep.getProvider().getParameterByName(AbstractAlignmentStepProvider.COLLECT_WGS_METRICS_NON_ZERO);
                    boolean doCollectWgsMetricsNonZero = wgsParamNonZero == null ? false : wgsParamNonZero.extractValue(getJob(), alignmentStep.getProvider(), alignmentStep.getStepIdx(), Boolean.class, false);

                    if (doCollectWgsMetricsNonZero)
                    {
                        getJob().getLogger().info("calculating wgs metrics over non-zero coverage");
                        getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING WGS METRICS OVER NON-ZERO COVERAGE");
                        File wgsMetricsFile = new File(renamedBam.getParentFile(), FileUtil.getBaseName(renamedBam) + ".wgsNonZero.metrics");
                        CollectWgsMetricsWithNonZeroCoverageWrapper wgsWrapper = new CollectWgsMetricsWithNonZeroCoverageWrapper(getJob().getLogger());
                        wgsWrapper.executeCommand(renamedBam, wgsMetricsFile, referenceGenome.getWorkingFastaFile());
                        getHelper().getFileManager().addOutput(metricsAction, "WGS Non-Zero Coverage Metrics File", wgsMetricsFile);
                        getHelper().getFileManager().addPicardMetricsFiles(Arrays.asList(new PipelineStepOutput.PicardMetricsOutput(wgsMetricsFile, renamedBam, rs.getRowId())));
                        getHelper().getFileManager().addCommandsToAction(wgsWrapper.getCommandsExecuted(), metricsAction);
                    }

                    //and insert size metrics
                    if (rs.hasPairedData())
                    {
                        getJob().getLogger().info("calculating insert size metrics");
                        getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING INSERT SIZE METRICS");
                        File metricsFile2 = new File(renamedBam.getParentFile(), FileUtil.getBaseName(renamedBam) + ".insertsize.metrics");
                        File metricsHistogram = new File(renamedBam.getParentFile(), FileUtil.getBaseName(renamedBam) + ".insertsize.metrics.pdf");
                        CollectInsertSizeMetricsWrapper collectInsertSizeMetricsWrapper = new CollectInsertSizeMetricsWrapper(getJob().getLogger());
                        if (collectInsertSizeMetricsWrapper.executeCommand(renamedBam, metricsFile2, metricsHistogram) != null)
                        {
                            getHelper().getFileManager().addOutput(metricsAction, "Insert Size Metrics File", metricsFile2);
                            getHelper().getFileManager().addOutput(metricsAction, "Insert Size Metrics Histogram", metricsHistogram);
                            getHelper().getFileManager().addPicardMetricsFiles(Arrays.asList(new PipelineStepOutput.PicardMetricsOutput(metricsFile2, renamedBam, rs.getRowId())));
                            getHelper().getFileManager().addCommandsToAction(collectInsertSizeMetricsWrapper.getCommandsExecuted(), metricsAction);
                        }
                    }

                    Date end = new Date();
                    metricsAction.setEndTime(end);
                    getJob().getLogger().info("Alignment Summary Metrics Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
                }
                else
                {
                    getJob().getLogger().info("BAM was not coordinate sorted or index was missing, skipping capture of alignment metrics");
                }
            }

            //generate stats
            if (so == SAMFileHeader.SortOrder.coordinate && index.exists())
            {
                if (alignmentStep.getProvider().shouldRunIdxstats())
                {
                    IdxStatsRunner idxRunner = new IdxStatsRunner(getJob().getLogger());
                    idxRunner.execute(renamedBam);
                    idxRunner.executeAndSave(renamedBam, new File(renamedBam.getParentFile(), "idxstats.txt"));
                    getHelper().getFileManager().addCommandsToAction(idxRunner.getCommandsExecuted(), metricsAction);
                }
                else
                {
                    getJob().getLogger().info("Idxstats will be skipped");
                }
            }
            else
            {
                getJob().getLogger().warn("BAM was either not coordinate sorted or lacks index");
            }

            _resumer.setAlignmentMetricsDone(true, metricsAction);
        }

        //perform remote analyses, if necessary
        getJob().setStatus(PipelineJob.TaskStatus.running, "PERFORMING ANALYSES");
        if (_resumer.isBamAnalysisDone())
        {
            getJob().getLogger().info("resuming BAM analyses from saved state");
        }
        else
        {
            List<PipelineStepCtx<AnalysisStep>> analysisSteps = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
            if (analysisSteps.isEmpty())
            {
                getJob().getLogger().debug("no analyses were selected");
            }
            else
            {
                List<RecordedAction> analysisActions = new ArrayList<>();
                getJob().setStatus(PipelineJob.TaskStatus.running, "PERFORMING ANALYSES ON ALIGNMENT");
                for (PipelineStepCtx<AnalysisStep> stepCtx : analysisSteps)
                {
                    getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING: " + stepCtx.getProvider().getLabel().toUpperCase());

                    getHelper().getJob().getLogger().info("Running analysis " + stepCtx.getProvider().getLabel() + " for readset: " + rs.getReadsetId());
                    getHelper().getJob().getLogger().info("\tUsing alignment: " + renamedBam.getPath());

                    RecordedAction action = new RecordedAction(stepCtx.getProvider().getLabel());
                    getHelper().getFileManager().addInput(action, "Input BAM File", renamedBam);
                    getHelper().getFileManager().addInput(action, "Reference DB FASTA", referenceGenome.getWorkingFastaFile());

                    AnalysisStep step = stepCtx.getProvider().create(getHelper());
                    step.setStepIdx(stepCtx.getStepIdx());
                    AnalysisStep.Output o = step.performAnalysisPerSampleRemote(rs, renamedBam, referenceGenome, renamedBam.getParentFile());
                    if (o != null)
                    {
                        getHelper().getFileManager().addStepOutputs(action, o);
                    }

                    analysisActions.add(action);
                    _resumer.setBamAnalysisComplete(analysisActions);
                }
            }
        }
    }

    private File doAlignment(ReferenceGenome referenceGenome, Readset rs, Map<ReadData, Pair<File, File>> files, List<RecordedAction> alignActions) throws PipelineJobException, IOException
    {
        if (files.isEmpty())
        {
            throw new PipelineJobException("readset has no files associated with it: " + rs.getName());
        }

        AlignmentStep alignmentStep = getHelper().getSingleStep(AlignmentStep.class).create(getHelper());
        String alignmentMode = StringUtils.trimToNull(alignmentStep.getProvider().getParameterByName(AbstractAlignmentStepProvider.ALIGNMENT_MODE_PARAM).extractValue(getPipelineJob(), alignmentStep.getProvider(), alignmentStep.getStepIdx()));
        getJob().getLogger().debug("alignment mode: " + alignmentMode);
        AbstractAlignmentStepProvider.ALIGNMENT_MODE mode = alignmentMode == null ? AbstractAlignmentStepProvider.ALIGNMENT_MODE.ALIGN_THEN_MERGE : AbstractAlignmentStepProvider.ALIGNMENT_MODE.valueOf(alignmentMode);
        if (mode == AbstractAlignmentStepProvider.ALIGNMENT_MODE.MERGE_THEN_ALIGN)
        {
            return doMergeThenAlign(referenceGenome, rs, files, alignActions);
        }
        else
        {
            return doAlignThenMerge(referenceGenome, rs, files, alignActions);
        }
    }

    private File doMergeThenAlign(ReferenceGenome referenceGenome, Readset rs, Map<ReadData, Pair<File, File>> files, List<RecordedAction> alignActions) throws PipelineJobException, IOException
    {
        AlignmentStep alignmentStep = getHelper().getSingleStep(AlignmentStep.class).create(getHelper());
        if (!alignmentStep.canAlignMultiplePairsAtOnce() && files.size() > 1)
        {
            getJob().setStatus(PipelineJob.TaskStatus.running, "Merging FASTQs");
            FastqMerger merger = new FastqMerger(getJob().getLogger());

            List<File> forward = new ArrayList<>();
            List<File> reverse = new ArrayList<>();
            RecordedAction mergeAction1 = new RecordedAction(MERGE_FASTQ_ACTIONNAME);
            Date start = new Date();
            mergeAction1.setStartTime(start);

            File doneFile = new File(getHelper().getWorkingDirectory(), "merge.done");
            Set<Integer> typesFound = new HashSet<>();
            for (Pair<File, File> pair : files.values())
            {
                forward.add(pair.first);
                getHelper().getFileManager().addInput(mergeAction1, "Input FASTQ", pair.first);

                if (pair.second != null)
                {
                    reverse.add(pair.second);
                }

                typesFound.add(pair.second == null ? 1 : 2);
            }

            if (typesFound.size() > 1)
            {
                throw new PipelineJobException("These data have a mixture of paired and unpaired reads");
            }

            File mergedForward = new File(getHelper().getWorkingDirectory(), SequenceTaskHelper.getMinimalBaseName(forward.get(0).getName()) + ".merged.R1.fastq.gz");
            if (doneFile.exists())
            {
                getJob().getLogger().info("FASTQ merge completed, skipping forward merge");
            }
            else
            {
                merger.mergeFiles(mergedForward, forward);
            }

            getHelper().getFileManager().addIntermediateFile(mergedForward);
            getHelper().getFileManager().addOutput(mergeAction1, "Merged FASTQ", mergedForward);

            Date end = new Date();
            mergeAction1.setEndTime(end);
            getJob().getLogger().info("Merge Forward FASTQs Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
            alignActions.add(mergeAction1);

            File mergedReverse = null;
            if (!reverse.isEmpty())
            {
                RecordedAction mergeAction2 = new RecordedAction(MERGE_FASTQ_ACTIONNAME);
                start = new Date();
                mergeAction2.setStartTime(start);
                reverse.forEach(x -> getHelper().getFileManager().addInput(mergeAction2, "Input FASTQ", x));

                mergedReverse = new File(getHelper().getWorkingDirectory(), SequenceTaskHelper.getMinimalBaseName(forward.get(0).getName()) + ".merged.R2.fastq.gz");
                if (doneFile.exists())
                {
                    getJob().getLogger().info("FASTQ merge completed, skipping reverse merge");
                }
                else
                {
                    merger.mergeFiles(mergedReverse, reverse);
                }

                getHelper().getFileManager().addIntermediateFile(mergedReverse);
                getHelper().getFileManager().addOutput(mergeAction2, "Merged FASTQ", mergedReverse);

                end = new Date();
                mergeAction2.setEndTime(end);
                getJob().getLogger().info("Merge Reverse FASTQs Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
                alignActions.add(mergeAction2);
            }

            FileUtils.touch(doneFile);
            getHelper().getFileManager().addIntermediateFile(doneFile);

            return doAlignmentForSet(Arrays.asList(Pair.of(mergedForward, mergedReverse)), referenceGenome, rs, -1, "", null);
        }
        else
        {
            getJob().getLogger().info("No FASTQ merge necessary");
            List<Pair<File, File>> inputs = new ArrayList<>(files.values());
            Optional<Integer> minReadData = files.keySet().stream().map(ReadData::getRowid).min(Integer::compareTo);
            return doAlignmentForSet(inputs, referenceGenome, rs, minReadData.get(), "", inputs.size() == 1 ? files.keySet().stream().iterator().next().getPlatformUnit() : null);
        }
    }

    private File doAlignThenMerge(ReferenceGenome referenceGenome, Readset rs, Map<ReadData, Pair<File, File>> files, List<RecordedAction> alignActions) throws PipelineJobException, IOException
    {
        File bam;
        List<File> alignOutputs = new ArrayList<>();
        int idx = 0;
        for (ReadData rd : files.keySet())
        {
            idx++;
            String msgSuffix = files.size() > 1 ? " (" + idx + " of " + files.size() + ")" : "";
            Pair<File, File> pair = files.get(rd);

            getJob().getLogger().info("Aligning inputs: " + pair.first.getName() + (pair.second == null ? "" : " and " + pair.second.getName()));

            alignOutputs.add(doAlignmentForSet(Arrays.asList(pair), referenceGenome, rs, rd.getRowid(), msgSuffix, rd.getPlatformUnit()));
        }

        //merge outputs
        if (alignOutputs.size() > 1)
        {
            getJob().setStatus(PipelineJob.TaskStatus.running, "MERGING BAM FILES");
            getJob().getLogger().info("merging BAMs");
            RecordedAction mergeAction = new RecordedAction(MERGE_ALIGNMENT_ACTIONNAME);
            Date start = new Date();
            mergeAction.setStartTime(start);
            MergeSamFilesWrapper mergeSamFilesWrapper = new MergeSamFilesWrapper(getJob().getLogger());
            List<File> bams = new ArrayList<>();
            for (File o : alignOutputs)
            {
                bams.add(o);
                getHelper().getFileManager().addInput(mergeAction, "Input BAM", o);
                getHelper().getFileManager().addIntermediateFile(o);
                getHelper().getFileManager().addIntermediateFile(new File(o.getPath() + ".bai"));
            }

            bam = new File(alignOutputs.get(0).getParent(), FileUtil.getBaseName(alignOutputs.get(0).getName()) + ".merged.bam");
            getHelper().getFileManager().addOutput(mergeAction, "Merged BAM", bam);
            //NOTE: merged BAMs will be deleted as intermediate files, and if we delete too early this breaks job resume
            mergeSamFilesWrapper.execute(bams, bam, false);
            getHelper().getFileManager().addCommandsToAction(mergeSamFilesWrapper.getCommandsExecuted(), mergeAction);

            Date end = new Date();
            mergeAction.setEndTime(end);
            getJob().getLogger().info("MergeSamFiles Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
            alignActions.add(mergeAction);
        }
        else
        {
            bam = alignOutputs.get(0);
        }

        return bam;
    }

    public File doAlignmentForSet(List<Pair<File, File>> inputFiles, ReferenceGenome referenceGenome, Readset rs, int lowestReadDataId, @NotNull String msgSuffix, @Nullable String platformUnit) throws PipelineJobException, IOException
    {
        AlignmentStep alignmentStep = getHelper().getSingleStep(AlignmentStep.class).create(getHelper());

        getJob().getLogger().info("Beginning alignment for: ");
        inputFiles.forEach(x -> getPipelineJob().getLogger().info(x.first.getName() + (x.second == null ? "" : " and " + x.second.getName()) + msgSuffix));

        if (_resumer.isReadDataAlignmentDone(lowestReadDataId))
        {
            getJob().getLogger().debug("resuming alignment of readData from saved state: " + lowestReadDataId);
            return _resumer.getBamForReadData(lowestReadDataId);
        }
        else
        {
            List<RecordedAction> actions = new ArrayList<>();

            //log input sequence count
            for (Pair<File, File> x : inputFiles)
            {
                FastqUtils.logSequenceCounts(x.first, x.second, getJob().getLogger(), null, null);
            }

            FileType gz = new FileType(".gz");
            boolean performedDecompress = false;
            for (Pair<File, File> pair : inputFiles)
            {
                if (!alignmentStep.supportsGzipFastqs() && gz.isType(pair.first))
                {
                    getJob().setStatus(PipelineJob.TaskStatus.running, "DECOMPRESS INPUT FILES");
                    getHelper().getFileManager().decompressInputFiles(pair, actions);
                    getHelper().getFileManager().addIntermediateFile(pair.first);
                    if (pair.second != null)
                    {
                        getHelper().getFileManager().addIntermediateFile(pair.second);
                    }

                    performedDecompress = true;
                }
            }

            if (!performedDecompress)
            {
                getJob().getLogger().debug("no need to decompress input files, skipping");
            }

            RecordedAction alignmentAction = new RecordedAction(ALIGNMENT_ACTIONNAME);

            Date start = new Date();
            alignmentAction.setStartTime(start);
            for (Pair<File, File> pair : inputFiles)
            {
                getHelper().getFileManager().addInput(alignmentAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, pair.first);

                if (pair.second != null)
                {
                    getHelper().getFileManager().addInput(alignmentAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, pair.second);
                }
            }

            getHelper().getFileManager().addInput(alignmentAction, IndexOutputImpl.REFERENCE_DB_FASTA, referenceGenome.getSourceFastaFile());

            File outputDirectory = new File(getHelper().getWorkingDirectory(), SequenceTaskHelper.getMinimalBaseName(inputFiles.get(0).first.getName()));
            outputDirectory = new File(outputDirectory, ALIGNMENT_SUBFOLDER_NAME);
            if (!outputDirectory.exists())
            {
                getJob().getLogger().debug("creating directory: " + outputDirectory.getPath());
                outputDirectory.mkdirs();
            }

            getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING: " + alignmentStep.getProvider().getLabel().toUpperCase() + msgSuffix);
            List<File> forwardFastqs = inputFiles.stream().map(Pair::getKey).collect(Collectors.toList());
            List<File> reverseFastqs = inputFiles.get(0).getValue() == null ? null : inputFiles.stream().map(Pair::getValue).collect(Collectors.toList());

            AlignmentStep.AlignmentOutput alignmentOutput = alignmentStep.performAlignment(rs, forwardFastqs, reverseFastqs, outputDirectory, referenceGenome, SequenceTaskHelper.getUnzippedBaseName(inputFiles.get(0).first.getName()) + "." + alignmentStep.getProvider().getName().toLowerCase(), String.valueOf(lowestReadDataId), platformUnit);
            getHelper().getFileManager().addStepOutputs(alignmentAction, alignmentOutput);

            if (alignmentOutput.getBAM() == null || !alignmentOutput.getBAM().exists())
            {
                throw new PipelineJobException("Unable to find BAM file after alignment: " + alignmentOutput.getBAM());
            }
            Date end = new Date();
            alignmentAction.setEndTime(end);
            getJob().getLogger().info(alignmentStep.getProvider().getLabel() + " Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
            actions.add(alignmentAction);

            SequenceUtil.logFastqBamDifferences(getJob().getLogger(), alignmentOutput.getBAM());

            ToolParameterDescriptor mergeParam = alignmentStep.getProvider().getParameterByName(AbstractAlignmentStepProvider.SUPPORT_MERGED_UNALIGNED);
            boolean doMergeUnaligned = mergeParam != null && mergeParam.extractValue(getJob(), alignmentStep.getProvider(), alignmentStep.getStepIdx(), Boolean.class, false);
            if (doMergeUnaligned)
            {
                getJob().setStatus(PipelineJob.TaskStatus.running, "MERGING UNALIGNED READS INTO BAM" + msgSuffix);
                getJob().getLogger().info("merging unaligned reads into BAM");
                File idx = new File(alignmentOutput.getBAM().getPath() + ".bai");
                if (idx.exists())
                {
                    getJob().getLogger().debug("deleting index: " + idx.getPath());
                    idx.delete();
                }

                //merge unaligned reads and clean file
                MergeBamAlignmentWrapper wrapper = new MergeBamAlignmentWrapper(getJob().getLogger());
                wrapper.executeCommand(referenceGenome.getWorkingFastaFile(), alignmentOutput.getBAM(), inputFiles, null);
                getHelper().getFileManager().addCommandsToAction(wrapper.getCommandsExecuted(), alignmentAction);
            }
            else
            {
                getJob().getLogger().info("skipping merge of unaligned reads on BAM");
            }

            if (alignmentStep.doAddReadGroups())
            {
                getJob().setStatus(PipelineJob.TaskStatus.running, "ADDING READ GROUPS" + msgSuffix);
                AddOrReplaceReadGroupsWrapper wrapper = new AddOrReplaceReadGroupsWrapper(getJob().getLogger());
                wrapper.executeCommand(alignmentOutput.getBAM(), null, rs.getReadsetId().toString(), rs.getPlatform(), (platformUnit == null ? rs.getReadsetId().toString() : platformUnit), rs.getName().replaceAll(" ", "_"));
                getHelper().getFileManager().addCommandsToAction(wrapper.getCommandsExecuted(), alignmentAction);
            }
            else
            {
                getJob().getLogger().info("skipping read group assignment");
            }

            //generate stats
            getJob().setStatus(PipelineJob.TaskStatus.running, "Generating BAM Stats");
            FlagStatRunner runner = new FlagStatRunner(getJob().getLogger());
            runner.execute(alignmentOutput.getBAM());
            getHelper().getFileManager().addCommandsToAction(runner.getCommandsExecuted(), alignmentAction);

            _resumer.setReadDataAlignmentDone(lowestReadDataId, actions, alignmentOutput.getBAM());

            return alignmentOutput.getBAM();
        }
    }

    public static class Resumer extends AbstractResumer
    {
        private File _workingFasta = null;
        @JsonSerialize(keyUsing = ObjectKeySerialization.Serializer.class, contentUsing = PairSerializer.class)
        @JsonDeserialize(keyUsing = ObjectKeySerialization.Deserializer.class)
        private Map<ReadData, Pair<File, File>> _filesToAlign = null;
        private File _mergedBamFile = null;
        private File _postProcessedBamFile = null;
        private File _sortedBamFile = null;
        private boolean _bamAnalysisDone = false;
        private boolean _alignmentMetricsDone = false;
        private File _renamedBamFile = null;
        private boolean _indexBamDone = false;
        private Map<Integer, File> _readDataBamMap = new HashMap<>();

        //for serialization
        public Resumer()
        {

        }

        private Resumer(SequenceAlignmentTask task)
        {
            super(task.getPipelineJob().getAnalysisDirectory(), task.getJob().getLogger(), task.getHelper().getFileManager());
        }

        public static Resumer create(SequenceAlignmentJob job, SequenceAlignmentTask task) throws PipelineJobException
        {
            //NOTE: allow a file in either local working dir or webserver dir.  if both exist, use the file most recently modified
            File file = null;
            for (File dir : Arrays.asList(job.getAnalysisDirectory(), task._wd.getDir()))
            {
                File toCheck = getSerializedJson(dir, JSON_NAME);
                if (toCheck.exists())
                {
                    job.getLogger().debug("inspecting file: " + toCheck.getPath());
                    if (file == null || file.lastModified() < toCheck.lastModified())
                    {
                        if (file != null)
                        {
                            job.getLogger().debug("choosing more recently modified file: " + toCheck.getPath());
                        }

                        file = toCheck;
                    }
                }
            }

            if (file != null)
            {
                try
                {
                    return createFromJson(job, task, file);
                }
                catch (Exception e)
                {
                    //allow for the possibility of a malformed file
                    job.getLogger().error("Error reading file: " +  file.getPath(), e);
                    file.delete();
                }
            }

            return new Resumer(task);
        }

        private static Resumer createFromJson(SequenceAlignmentJob job, SequenceAlignmentTask task, File file) throws PipelineJobException
        {
            Resumer ret = readFromJson(file, Resumer.class);
            ret._isResume = true;
            ret.setLogger(task.getJob().getLogger());
            ret.setWebserverJobDir(task.getPipelineJob().getAnalysisDirectory());
            ret.getFileManager().onResume(job, task._wd);

            task._taskHelper.setFileManager(ret.getFileManager());
            try
            {
                if (!ret._copiedInputs.isEmpty())
                {
                    for (File orig : ret._copiedInputs.keySet())
                    {
                        task._wd.inputFile(orig, ret._copiedInputs.get(orig), false);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            //debugging:
            job.getLogger().debug("loaded from file.  total recorded actions: " + ret.getRecordedActions().size());
            for (RecordedAction a : ret.getRecordedActions())
            {
                job.getLogger().debug("action: " + a.getName() + ", inputs: " + a.getInputs().size() + ", outputs: " + a.getOutputs().size());
            }

            if (ret._recordedActions == null)
            {
                throw new PipelineJobException("Job read from file, but did not have any saved actions.  This indicates a problem w/ serialization.");
            }

            return ret;
        }

        protected static String JSON_NAME = "alignmentCheckpoint.json";

        @Override
        protected String getJsonName()
        {
            return JSON_NAME;
        }

        public void setHasCopiedResources(File workingFasta, List<RecordedAction> actions, Map<File, File> copiedInputs) throws PipelineJobException
        {
            _workingFasta = workingFasta;
            _recordedActions.addAll(actions);
            _copiedInputs.putAll(copiedInputs);
            saveState();
        }

        public boolean hasCopiedResources()
        {
            return _workingFasta != null;
        }

        public boolean isFastqPreprocessingDone()
        {
            return _filesToAlign != null;
        }

        public void setFastqPreprocessingDone(Map<ReadData, Pair<File, File>> toAlign, List<RecordedAction> actions, Map<File, File> copiedInputs) throws PipelineJobException
        {
            _filesToAlign = toAlign;
            _recordedActions.addAll(actions);
            _copiedInputs.putAll(copiedInputs);
            saveState();
        }

        public boolean isInitialAlignmentDone()
        {
            return _mergedBamFile != null;
        }

        public void setInitialAlignmentDone(File mergedBamFile, List<RecordedAction> actions) throws PipelineJobException
        {
            if (mergedBamFile == null)
            {
                throw new PipelineJobException("BAM is null");
            }

            _mergedBamFile = mergedBamFile;
            _recordedActions.addAll(actions);
            saveState();
        }

        public boolean isBamPostProcessingBamDone()
        {
            return _postProcessedBamFile != null;
        }

        public void setBamPostProcessingBamDone(File postProcessedBamFile, List<RecordedAction> actions) throws PipelineJobException
        {
            _postProcessedBamFile = postProcessedBamFile;
            _recordedActions.addAll(actions);
            saveState();
        }

        public boolean isBamSortDone()
        {
            return _sortedBamFile != null;
        }

        public void setBamSortDone(File sortedBamFile, @Nullable RecordedAction action) throws PipelineJobException
        {
            _sortedBamFile = sortedBamFile;
            if (action != null)
            {
                _recordedActions.add(action);
            }

            saveState();
        }

        public boolean isBamRenameDone()
        {
            return _renamedBamFile != null;
        }

        public void setBamRenameDone(File renamedBamFile, List<RecordedAction> actions) throws PipelineJobException
        {
            _renamedBamFile = renamedBamFile;
            _recordedActions.addAll(actions);
            saveState();
        }

        public File getRenamedBamFile()
        {
            return _renamedBamFile;
        }

        public void setRenamedBamFile(File renamedBamFile)
        {
            _renamedBamFile = renamedBamFile;
        }

        public void setBamAnalysisComplete(List<RecordedAction> actions) throws PipelineJobException
        {
            _bamAnalysisDone = true;
            _recordedActions.addAll(actions);
            saveState();
        }

        public File getWorkingFasta()
        {
            return _workingFasta;
        }

        public void setWorkingFasta(File workingFasta)
        {
            _workingFasta = workingFasta;
        }

        public Map<ReadData, Pair<File, File>> getFilesToAlign()
        {
            return _filesToAlign;
        }

        public void setFilesToAlign(Map<ReadData, Pair<File, File>> filesToAlign)
        {
            _filesToAlign = filesToAlign;
        }

        public File getMergedBamFile()
        {
            return _mergedBamFile;
        }

        public void setMergedBamFile(File mergedBamFile)
        {
            _mergedBamFile = mergedBamFile;
        }

        public File getPostProcessedBamFile()
        {
            return _postProcessedBamFile;
        }

        public void setPostProcessedBamFile(File postProcessedBamFile)
        {
            _postProcessedBamFile = postProcessedBamFile;
        }

        public File getSortedBamFile()
        {
            return _sortedBamFile;
        }

        public void setSortedBamFile(File sortedBamFile)
        {
            _sortedBamFile = sortedBamFile;
        }

        public boolean isBamAnalysisDone()
        {
            return _bamAnalysisDone;
        }

        public void setBamAnalysisDone(boolean bamAnalysisDone)
        {
            _bamAnalysisDone = bamAnalysisDone;
        }

        public boolean isAlignmentMetricsDone()
        {
            return _alignmentMetricsDone;
        }

        public void setAlignmentMetricsDone(boolean alignmentMetricsDone)
        {
            _alignmentMetricsDone = alignmentMetricsDone;
        }

        public boolean isIndexBamDone()
        {
            return _indexBamDone;
        }

        public void setIndexBamDone(boolean indexBamDone)
        {
            _indexBamDone = indexBamDone;
        }

        public void setIndexBamDone(boolean indexBamDone, @Nullable RecordedAction action) throws PipelineJobException
        {
            _indexBamDone = indexBamDone;
            if (action != null)
            {
                _recordedActions.add(action);
            }
            saveState();
        }

        public void setAlignmentMetricsDone(boolean alignmentMetricsDone, RecordedAction action) throws PipelineJobException
        {
            _alignmentMetricsDone = alignmentMetricsDone;
            if (action != null)
            {
                _recordedActions.add(action);
            }
            saveState();
        }

        public boolean isReadDataAlignmentDone(int readDataId)
        {
            return _readDataBamMap.containsKey(readDataId);
        }

        public void setReadDataAlignmentDone(int readDataId, List<RecordedAction> actions, File bam) throws PipelineJobException
        {
            getLogger().debug("setting read data alignment done: " + readDataId + ", " + (actions == null ? "0" : actions.size()) + " actions");
            _readDataBamMap.put(readDataId, bam);
            if (actions != null)
            {
                _recordedActions.addAll(actions);
            }
            saveState();
        }

        public File getBamForReadData(int readDataId)
        {
            return _readDataBamMap.get(readDataId);
        }

        public Map<Integer, File> getReadDataBamMap()
        {
            return _readDataBamMap;
        }

        public void setReadDataBamMap(Map<Integer, File> readDataBamMap)
        {
            _readDataBamMap = readDataBamMap;
        }
    }

    public static class TestCase extends Assert
    {
        private static final Logger _log = LogManager.getLogger(TestCase.class);

        @Test
        public void serializeRecordedActionTest() throws Exception
        {
            RecordedAction action1 = new RecordedAction();
            action1.setName("Action1");
            action1.setDescription("Description");
            action1.addInput(new File("/input"), "Input");
            action1.addOutput(new File("/output"), "Output", false);

            File tmp = new File(System.getProperty("java.io.tmpdir"));
            File xml = new File(tmp, Resumer.JSON_NAME);

            ObjectMapper objectMapper = PipelineJob.createObjectMapper();
            objectMapper.writeValue(xml, action1);

            RecordedAction action = objectMapper.readValue(xml, RecordedAction.class);
            assertEquals("Action1", action.getName());
        }

        @Test
        public void serializeTest() throws Exception
        {
            Resumer r = new Resumer();
            r.setLogger(_log);
            r.setRecordedActions(new LinkedHashSet<>());
            RecordedAction action1 = new RecordedAction();
            action1.setName("Action1");
            action1.setDescription("Description");
            action1.addInput(new File("/input"), "Input");
            action1.addOutput(new File("/output"), "Output", false);
            r.getRecordedActions().add(action1);
            r.setFileManager(new TaskFileManagerImpl());
            SequenceOutputFile so = new SequenceOutputFile();
            so.setName("so1");
            r.getFileManager().addSequenceOutput(so);

            File file1 = new File("file1");
            File file2 = new File("file2");
            Pair<File, File> pair = Pair.of(file1, file2);

            ReadDataImpl rd = new ReadDataImpl();
            rd.setFile(file1, 1);
            rd.setFile(file2, 2);
            rd.setReadset(-1);
            rd.setRowid(-1);
            r._filesToAlign = new LinkedHashMap<>();
            r._filesToAlign.put(rd, pair);

            File tmp = new File(System.getProperty("java.io.tmpdir"));
            File file = new File(tmp, Resumer.JSON_NAME);
            r.writeToJson(tmp);

            //after deserialization the RecordedAction should match the original
            Resumer r2 = Resumer.readFromJson(file, Resumer.class);
            assertEquals(1, r2.getRecordedActions().size());
            RecordedAction action2 = r2.getRecordedActions().iterator().next();
            assertEquals("Action1", action2.getName());
            assertEquals("Description", action2.getDescription());
            assertEquals(1, action2.getInputs().size());
            assertEquals(new File("/input").toURI(), action1.getInputs().iterator().next().getURI());
            assertEquals(1, action2.getOutputs().size());
            assertEquals(new File("/output").toURI(), action2.getOutputs().iterator().next().getURI());

            assertEquals(1, r2.getFileManager().getOutputsToCreate().size());
            assertEquals("so1", r2.getFileManager().getOutputsToCreate().iterator().next().getName());

            assertEquals(1, r2.getFilesToAlign().size());
            Pair<File, File> p2 = r2.getFilesToAlign().values().iterator().next();
            assertEquals(file1.getAbsoluteFile(), p2.first.getAbsoluteFile());
            assertEquals(file2.getAbsoluteFile(), p2.second.getAbsoluteFile());

            file.delete();
        }
    }
}

