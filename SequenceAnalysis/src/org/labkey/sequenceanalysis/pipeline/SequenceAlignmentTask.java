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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.XppDriver;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.run.alignment.AlignerIndexUtil;
import org.labkey.sequenceanalysis.run.bampostprocessing.SortSamStep;
import org.labkey.sequenceanalysis.run.preprocessing.TrimmomaticWrapper;
import org.labkey.sequenceanalysis.run.util.AddOrReplaceReadGroupsWrapper;
import org.labkey.sequenceanalysis.run.util.AlignmentSummaryMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.BuildBamIndexWrapper;
import org.labkey.sequenceanalysis.run.util.CollectInsertSizeMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.CollectWgsMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.FastaIndexer;
import org.labkey.sequenceanalysis.run.util.FlagStatRunner;
import org.labkey.sequenceanalysis.run.util.MergeBamAlignmentWrapper;
import org.labkey.sequenceanalysis.run.util.MergeSamFilesWrapper;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
            getHelper().getFileManager().cleanup(_resumer.getRecordedActions());
            _resumer.markComplete();
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

            pair = preprocessFastq(pair.first, pair.second, preprocessingActions, suffix);
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
        String val = getJob().getParameters().get(AlignmentInitTask.COPY_LOCALLY);
        boolean doCopy = val == null ? true : ConvertHelper.convert(val, Boolean.class);

        //but let aligners override this.
        AlignmentStep alignmentStep = getHelper().getSingleStep(AlignmentStep.class).create(getHelper());
        if (alignmentStep.alwaysCopyIndexToWorkingDir())
        {
            getJob().getLogger().info("The selected aligner requires a local copy of the FASTA, so it will be copied");
            doCopy = true;
        }

        File localCachedIndexDir = SequencePipelineService.get().getRemoteGenomeCacheDirectory();
        if (localCachedIndexDir != null)
        {
            AlignerIndexUtil.cacheGenomeLocally(referenceGenome, localCachedIndexDir, getJob().getLogger());
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
    public Pair<File, File> preprocessFastq(File inputFile1, @Nullable File inputFile2, List<RecordedAction> actions, String statusSuffix) throws PipelineJobException, IOException
    {
        getJob().setStatus(PipelineJob.TaskStatus.running, PREPROCESS_FASTQ_STATUS);
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
            //combine providers as needed
            List<PipelineStepProvider<PreprocessingStep>> combinedProviders = new ArrayList<>();
            combinedProviders.add(providers.remove(0));
            while (!providers.isEmpty())
            {
                PipelineStepProvider<PreprocessingStep> previous = combinedProviders.get(combinedProviders.size() - 1);
                PipelineStepProvider combinedStep = previous.combineSteps(providers.get(0));
                if (combinedStep != null)
                {
                    //add this combined step to replace the previous
                    getJob().getLogger().debug("combining step: " + previous.getLabel() + " with " + providers.get(0).getLabel());
                    combinedProviders.remove(combinedProviders.size() - 1);
                    combinedProviders.add(combinedStep);
                }
                else
                {
                    //otherwise append to the new list and remove from this list
                    combinedProviders.add(providers.get(0));
                }

                providers.remove(0);
            }

            providers = combinedProviders;

            String originalbaseName = SequenceTaskHelper.getMinimalBaseName(inputFile1.getName());
            String originalbaseName2 = null;

            //log read count:
            Pair<Long, Long> previousCounts = FastqUtils.logSequenceCounts(inputFile1, inputFile2, getJob().getLogger(), null, null);

            if (inputFile2 != null)
            {
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
                Date start = new Date();
                action.setStartTime(start);
                getHelper().getFileManager().addInput(action, "Input FASTQ", pair.first);
                if (inputFile2 != null)
                {
                    getHelper().getFileManager().addInput(action, "Input FASTQ", pair.second);
                }

                PreprocessingStep step = provider.create(getHelper());
                getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING: " + provider.getLabel().toUpperCase() + statusSuffix);
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
                getJob().getLogger().info(provider.getLabel() + " Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
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

            List<AlignmentStep.AlignmentOutput> outputs = new ArrayList<>();
            int idx = 0;
            for (ReadData rd : files.keySet())
            {
                idx++;
                String msgSuffix = files.size() > 1 ? " (" + idx + " of " + files.size() + ")" : "";
                Pair<File, File> pair = files.get(rd);

                getJob().getLogger().info("Aligning inputs: " + pair.first.getName() + (pair.second == null ? "" : " and " + pair.second.getName()));

                outputs.add(doAlignmentForPair(alignActions, pair, referenceGenome, rs, rd, msgSuffix));
            }

            //merge outputs
            if (outputs.size() > 1)
            {
                getJob().setStatus(PipelineJob.TaskStatus.running, "MERGING BAM FILES");
                getJob().getLogger().info("merging BAMs");
                RecordedAction mergeAction = new RecordedAction(MERGE_ALIGNMENT_ACTIONNAME);
                Date start = new Date();
                mergeAction.setStartTime(start);
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
                getHelper().getFileManager().addCommandsToAction(mergeSamFilesWrapper.getCommandsExecuted(), mergeAction);

                Date end = new Date();
                mergeAction.setEndTime(end);
                getJob().getLogger().info("MergeSamFiles Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
                alignActions.add(mergeAction);
            }
            else
            {
                bam = outputs.get(0).getBAM();
            }

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
            List<PipelineStepProvider<BamProcessingStep>> providers = SequencePipelineService.get().getSteps(getJob(), BamProcessingStep.class);
            if (providers.isEmpty())
            {
                getJob().getLogger().info("No BAM postprocessing is necessary");
            }
            else
            {
                getJob().getLogger().info("***Starting BAM Post processing");
                getJob().setStatus(PipelineJob.TaskStatus.running, "BAM POST-PROCESSING");
                getHelper().getFileManager().addIntermediateFile(bam);

                for (PipelineStepProvider<BamProcessingStep> provider : providers)
                {
                    getJob().getLogger().info("performing step: " + provider.getLabel());
                    getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING: " + provider.getLabel().toUpperCase());
                    getJob().getLogger().debug("BAM index exists: " + (new File(bam.getPath() + ".bai")).exists());

                    RecordedAction action = new RecordedAction(provider.getLabel());
                    Date start = new Date();
                    action.setStartTime(start);
                    getHelper().getFileManager().addInput(action, "Input BAM", bam);

                    BamProcessingStep step = provider.create(getHelper());
                    BamProcessingStep.Output output = step.processBam(rs, bam, referenceGenome, bam.getParentFile());
                    getHelper().getFileManager().addStepOutputs(action, output);

                    if (output.getBAM() != null)
                    {
                        bam = output.getBAM();

                        //can take a long time to execute
                        //getJob().getLogger().info("\ttotal alignments in processed BAM: " + SequenceUtil.getAlignmentCount(bam));
                        getJob().getLogger().info("\tfile size: " + FileUtils.byteCountToDisplaySize(bam.length()));
                    }
                    else
                    {
                        getJob().getLogger().info("no BAM created by step, using output from previous step");
                    }

                    getJob().getLogger().debug("index exists: " + (new File(bam.getPath() + ".bai")).exists());

                    Date end = new Date();
                    action.setEndTime(end);
                    getJob().getLogger().info(provider.getLabel() + " Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
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
                }

                if (renamedBam.exists())
                {
                    getJob().getLogger().debug("deleting existing BAM: " + renamedBam.getName());
                    renamedBam.delete();
                }
                FileUtils.moveFile(bam, renamedBam);

                File bamIdxOrig = new File(bam.getPath() + ".bai");
                File finalBamIdx = new File(renamedBam.getPath() + ".bai");
                if (bamIdxOrig.exists())
                {
                    getJob().getLogger().debug("also moving bam index");
                    if (finalBamIdx.exists())
                    {
                        getJob().getLogger().warn("unexpected file, deleting: " + finalBamIdx.getPath());
                    }

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
            if (alignmentStep.doSortIndexBam())
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
                boolean doCollectWgsMetrics = wgsParam == null ? false : wgsParam.extractValue(getJob(), alignmentStep.getProvider(), Boolean.class, false);

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
                getJob().getLogger().info("BAM was not coordinate sorted, skipping capture of alignment metrics");
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
            List<PipelineStepProvider<AnalysisStep>> analysisProviders = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
            if (analysisProviders.isEmpty())
            {
                getJob().getLogger().debug("no analyses were selected");
            }
            else
            {
                List<RecordedAction> analysisActions = new ArrayList<>();
                getJob().setStatus(PipelineJob.TaskStatus.running, "PERFORMING ANALYSES ON ALIGNMENT");
                for (PipelineStepProvider<AnalysisStep> provider : analysisProviders)
                {
                    getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING: " + provider.getLabel().toUpperCase());

                    getHelper().getJob().getLogger().info("Running analysis " + provider.getLabel() + " for readset: " + rs.getReadsetId());
                    getHelper().getJob().getLogger().info("\tUsing alignment: " + renamedBam.getPath());

                    RecordedAction action = new RecordedAction(provider.getLabel());
                    getHelper().getFileManager().addInput(action, "Input BAM File", renamedBam);
                    getHelper().getFileManager().addInput(action, "Reference DB FASTA", referenceGenome.getWorkingFastaFile());

                    AnalysisStep step = provider.create(getHelper());
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

    public AlignmentStep.AlignmentOutput doAlignmentForPair(List<RecordedAction> actions, Pair<File, File> inputFiles, ReferenceGenome referenceGenome, Readset rs, ReadData rd, String msgSuffix) throws PipelineJobException, IOException
    {
        //log input sequence count
        FastqUtils.logSequenceCounts(inputFiles.first, inputFiles.second, getJob().getLogger(), null, null);

        AlignmentStep alignmentStep = getHelper().getSingleStep(AlignmentStep.class).create(getHelper());
        FileType gz = new FileType(".gz");
        if (!alignmentStep.supportsGzipFastqs() && gz.isType(inputFiles.first))
        {
            getJob().setStatus(PipelineJob.TaskStatus.running, "DECOMPRESS INPUT FILES");
            getHelper().getFileManager().decompressInputFiles(inputFiles, actions);
            getHelper().getFileManager().addIntermediateFile(inputFiles.first);
            getHelper().getFileManager().addIntermediateFile(inputFiles.second);
        }
        else
        {
            getJob().getLogger().debug("no need to decompress input files, skipping");
        }

        RecordedAction alignmentAction = new RecordedAction(ALIGNMENT_ACTIONNAME);

        Date start = new Date();
        alignmentAction.setStartTime(start);
        getHelper().getFileManager().addInput(alignmentAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, inputFiles.first);

        if (inputFiles.second != null)
        {
            getHelper().getFileManager().addInput(alignmentAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, inputFiles.second);
        }

        getHelper().getFileManager().addInput(alignmentAction, AlignmentInitTask.REFERENCE_DB_FASTA, referenceGenome.getSourceFastaFile());
        getJob().getLogger().info("Beginning alignment for: " + inputFiles.first.getName() + (inputFiles.second == null ? "" : " and " + inputFiles.second.getName()) + msgSuffix);

        File outputDirectory = new File(getHelper().getWorkingDirectory(), SequenceTaskHelper.getMinimalBaseName(inputFiles.first.getName()));
        outputDirectory = new File(outputDirectory, ALIGNMENT_SUBFOLDER_NAME);
        if (!outputDirectory.exists())
        {
            getJob().getLogger().debug("creating directory: " + outputDirectory.getPath());
            outputDirectory.mkdirs();
        }

        getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING: " + alignmentStep.getProvider().getLabel().toUpperCase() + msgSuffix);
        AlignmentStep.AlignmentOutput alignmentOutput = alignmentStep.performAlignment(rs, inputFiles.first, inputFiles.second, outputDirectory, referenceGenome, SequenceTaskHelper.getUnzippedBaseName(inputFiles.first.getName()) + "." + alignmentStep.getProvider().getName().toLowerCase());
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
        boolean doMergeUnaligned = mergeParam == null ? false : mergeParam.extractValue(getJob(), alignmentStep.getProvider(), Boolean.class, false);
        if (doMergeUnaligned)
        {
            getJob().setStatus(PipelineJob.TaskStatus.running, "MERGING UNALIGNED READS INTO BAM" + (msgSuffix != null ? ": " + msgSuffix : ""));
            getJob().getLogger().info("merging unaligned reads into BAM");
            File idx = new File(alignmentOutput.getBAM().getPath() + ".bai");
            if (idx.exists())
            {
                getJob().getLogger().debug("deleting index: " + idx.getPath());
                idx.delete();
            }

            //merge unaligned reads and clean file
            MergeBamAlignmentWrapper wrapper = new MergeBamAlignmentWrapper(getJob().getLogger());
            wrapper.executeCommand(referenceGenome.getWorkingFastaFile(), alignmentOutput.getBAM(), inputFiles.first, inputFiles.second, null);
            getHelper().getFileManager().addCommandsToAction(wrapper.getCommandsExecuted(), alignmentAction);
        }
        else
        {
            getJob().getLogger().info("skipping merge of unaligned reads on BAM");
        }

        if (alignmentStep.doAddReadGroups())
        {
            getJob().setStatus(PipelineJob.TaskStatus.running, "ADDING READ GROUPS" + (msgSuffix != null ? ": " + msgSuffix : ""));
            AddOrReplaceReadGroupsWrapper wrapper = new AddOrReplaceReadGroupsWrapper(getJob().getLogger());
            wrapper.executeCommand(alignmentOutput.getBAM(), null, rs.getReadsetId().toString(), rs.getPlatform(), (rd.getPlatformUnit() == null ? rs.getReadsetId().toString() : rd.getPlatformUnit()), rs.getName().replaceAll(" ", "_"));
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

        return alignmentOutput;
    }

    public static class Resumer implements Serializable
    {
        transient SequenceAlignmentTask _task;
        transient Logger _log;
        transient static final XStream _xstream = new XStream(new XppDriver());

        private TaskFileManagerImpl _fileManager;
        private LinkedHashSet<RecordedAction> _recordedActions = null;
        private Map<File, File> _copiedInputs = new HashMap<>();
        private boolean _isResume = false;

        private File _workingFasta = null;
        private Map<ReadData, Pair<File, File>> _filesToAlign = null;
        private File _mergedBamFile = null;
        private File _postProcessedBamFile = null;
        private File _sortedBamFile = null;
        private boolean _bamAnalysisDone = false;
        private boolean _alignmentMetricsDone = false;
        private File _renamedBamFile = null;
        private boolean _indexBamDone = false;

        //for serialization
        public Resumer()
        {

        }

        private Resumer(SequenceAlignmentTask task)
        {
            _task = task;
            _log = task.getJob().getLogger();
            _fileManager = _task.getHelper().getFileManager();
            _recordedActions = new LinkedHashSet<>();
        }

        public static Resumer create(SequenceAlignmentJob job, SequenceAlignmentTask task) throws PipelineJobException
        {
            File xml = getSerializedXml(job.getAnalysisDirectory());
            if (!xml.exists())
            {
                return new Resumer(task);
            }
            else
            {
                Resumer ret = readFromXml(xml);
                ret._isResume = true;
                ret._task = task;
                ret._log = task.getJob().getLogger();
                ret._fileManager._job = job;
                ret._fileManager._wd = task._wd;
                ret._fileManager._workLocation = task._wd.getDir();
                ret._task._taskHelper.setFileManager(ret._fileManager);
                try
                {
                    if (!ret._copiedInputs.isEmpty())
                    {
                        for (File orig : ret._copiedInputs.keySet())
                        {
                            ret._task._wd.inputFile(orig, ret._copiedInputs.get(orig), false);
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                //debugging:
                job.getLogger().debug("loaded from XML.  total recorded actions: " + ret.getRecordedActions().size());
                for (RecordedAction a : ret.getRecordedActions())
                {
                    job.getLogger().debug("action: " + a.getName() + ", inputs: " + a.getInputs().size() + ", outputs: " + a.getOutputs().size());
                }

                if (ret._recordedActions == null)
                {
                    throw new PipelineJobException("Job read from XML, but did not have any saved actions.  This indicates a problem w/ serialization.");
                }

                return ret;
            }
        }

        private static String XML_NAME = "alignmentCheckpoint.xml";

        private static File getSerializedXml(File outdir)
        {
            return new File(outdir, XML_NAME);
        }

        private static Resumer readFromXml(File xml) throws PipelineJobException
        {
            try (BufferedInputStream bus = new BufferedInputStream(new FileInputStream(xml)))
            {
                return (Resumer)_xstream.fromXML(bus);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private void writeToXml() throws PipelineJobException
        {
            writeToXml(_task.getPipelineJob().getAnalysisDirectory());
        }

        private void writeToXml(File outDir) throws PipelineJobException
        {
            _log.debug("saving job checkpoint to file");
            _log.debug("total actions: " + _recordedActions.size());
            try (PrintWriter writer = PrintWriters.getPrintWriter(getSerializedXml(outDir)))
            {
                String xml = _xstream.toXML(this);
                writer.write(xml);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        public void markComplete()
        {
            File xml = getSerializedXml(_task.getPipelineJob().getAnalysisDirectory());
            if (xml.exists())
            {
                _log.info("closing job resumer");
                xml.delete();
            }
        }
        
        public void saveState() throws PipelineJobException
        {
            writeToXml();
        }

        public boolean isResume()
        {
            return _isResume;
        }

        public void setHasCopiedResources(File workingFasta, List<RecordedAction> actions, Map<File, File> copiedInputs) throws PipelineJobException
        {
            _workingFasta = workingFasta;
            for (RecordedAction action : actions)
            {
                _recordedActions.add(action);
            }
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
            for (RecordedAction action : actions)
            {
                _recordedActions.add(action);
            }
            _copiedInputs.putAll(copiedInputs);
            saveState();
        }

        public boolean isInitialAlignmentDone()
        {
            return _mergedBamFile != null;
        }

        public void setInitialAlignmentDone(File mergedBamFile, List<RecordedAction> actions) throws PipelineJobException
        {
            _mergedBamFile = mergedBamFile;
            for (RecordedAction action : actions)
            {
                _recordedActions.add(action);
            }
            saveState();
        }

        public boolean isBamPostProcessingBamDone()
        {
            return _postProcessedBamFile != null;
        }

        public void setBamPostProcessingBamDone(File postProcessedBamFile, List<RecordedAction> actions) throws PipelineJobException
        {
            _postProcessedBamFile = postProcessedBamFile;
            for (RecordedAction action : actions)
            {
                _recordedActions.add(action);
            }
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
            for (RecordedAction action : actions)
            {
                _recordedActions.add(action);
            }

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
            for (RecordedAction action : actions)
            {
                _recordedActions.add(action);
            }
            saveState();
        }

        public TaskFileManagerImpl getFileManager()
        {
            return _fileManager;
        }

        public void setFileManager(TaskFileManagerImpl fileManager)
        {
            _fileManager = fileManager;
        }

        public Map<File, File> getCopiedInputs()
        {
            return _copiedInputs;
        }

        public void setCopiedInputs(Map<File, File> copiedInputs)
        {
            _copiedInputs = copiedInputs;
        }

        public void setResume(boolean resume)
        {
            _isResume = resume;
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

        public LinkedHashSet<RecordedAction> getRecordedActions()
        {
            return _recordedActions;
        }

        public void setRecordedActions(LinkedHashSet<RecordedAction> recordedActions)
        {
            _recordedActions = recordedActions;
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
    }

    public static class TestCase extends Assert
    {
        private static final Logger _log = Logger.getLogger(TestCase.class);

        @Test
        public void serializeTest() throws Exception
        {
            Resumer r = new Resumer();
            r._log = _log;
            r._recordedActions = new LinkedHashSet<>();
            RecordedAction action1 = new RecordedAction();
            action1.setName("Action1");
            action1.setDescription("Description");
            action1.addInput(new File("/input"), "Input");
            action1.addOutput(new File("/output"), "Output", false);
            r._recordedActions.add(action1);

            File tmp = new File(System.getProperty("java.io.tmpdir"));
            File xml = new File(tmp, Resumer.XML_NAME);
            r.writeToXml(tmp);

            //after deserialization the RecordedAction should match the original
            Resumer r2 = Resumer.readFromXml(xml);
            assertEquals(1, r2._recordedActions.size());
            RecordedAction action2 = r2._recordedActions.iterator().next();
            assertEquals("Action1", action2.getName());
            assertEquals("Description", action2.getDescription());
            assertEquals(1, action2.getInputs().size());
            assertEquals(new File("/input").toURI(), action1.getInputs().iterator().next().getURI());
            assertEquals(1, action2.getOutputs().size());
            assertEquals(new File("/output").toURI(), action2.getOutputs().iterator().next().getURI());

            xml.delete();
        }
    }
}

