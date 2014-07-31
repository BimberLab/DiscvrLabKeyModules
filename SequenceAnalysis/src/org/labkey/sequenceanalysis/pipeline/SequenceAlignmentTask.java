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

import net.sf.picard.sam.BuildBamIndex;
import net.sf.samtools.SAMFileReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.api.pipeline.AlignmentStep;
import org.labkey.sequenceanalysis.api.pipeline.BamProcessingStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.PreprocessingStep;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceLibraryStep;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.run.bampostprocessing.BamProcessingOutputImpl;
import org.labkey.sequenceanalysis.run.bampostprocessing.SortSamStep;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
    public static final String FINAL_BAM_ROLE = "Final Alignment";
    public static final String FINAL_BAM_INDEX_ROLE = "Final Alignment Index";

    //public static final String PREPROCESS_FASTQ_ACTIONNAME = "Preprocess FASTQ";
    public static final String ALIGNMENT_ACTIONNAME = "Performing Alignment";
    public static final String SORT_BAM_ACTION = "Sorting BAM";
    public static final String INDEX_ACTION = "Indexing BAM";

    //public static final String MERGE_ALIGNMENT_ACTIONNAME = "Merging Alignments";
    public static final String DECOMPRESS_ACTIONNAME = "Decompressing Inputs";
    private static final String ALIGNMENT_SUBFOLDER_NAME = "Alignment";  //the subfolder within which alignment data will be placed
    private static final String COPY_REFERENCE_LIBRARY_ACTIONNAME = "Copy Reference Library";

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
            setJoin(false);
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
            allowableNames.add(COPY_REFERENCE_LIBRARY_ACTIONNAME);

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
            List<File> inputFiles = new ArrayList<>();
            inputFiles.addAll(getHelper().getSupport().getInputFiles());
            getHelper().getFileManager().decompressInputFiles(inputFiles, actions);

            //then we preprocess FASTQ files, if needed
            Map<ReadsetModel, Pair<File, File>> groupedFiles = getAlignmentFiles(getJob(), inputFiles, true);
            if (groupedFiles.size() > 0)
            {
                File refFasta = copyReferenceLibary(actions);
                for (ReadsetModel rs : groupedFiles.keySet()){
                    Pair<File, File> pair = groupedFiles.get(rs);
                    String outputBasename = FileUtil.getBaseName(pair.first);
                    pair = preprocessFastq(pair.first, pair.second, actions);

                    //do align
                    if (SequenceTaskHelper.isAlignmentUsed(getJob()))
                    {
                        getJob().getLogger().info("Preparing to perform alignment");
                        alignSet(rs, outputBasename, Arrays.asList(pair), actions, refFasta);
                    }
                    else
                    {
                        getJob().getLogger().info("Alignment not selected, skipping");
                    }
                }
            }

            //TODO
//            //compress alignment inputs if created by pre-processing
//            //if we are going to delete intermediates anyway, skip this
//            if (!getHelper().getFileManager().isDeleteIntermediateFiles() && preprocessedOutputs.size() > 0)
//            {
//                getJob().getLogger().info("Compressing input FASTQ files");
//                for (File file : preprocessedOutputs)
//                {
//                    getHelper().getFileManager().compressFile(file);
//                }
//            }

            //remove unzipped files
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

    private File copyReferenceLibary(List<RecordedAction> actions) throws PipelineJobException
    {
        File sharedDirectory = new File(getHelper().getSupport().getAnalysisDirectory(), SequenceTaskHelper.SHARED_SUBFOLDER_NAME);
        File refFasta = getHelper().getSingleStep(ReferenceLibraryStep.class).create(getHelper()).getExpectedFastaFile(sharedDirectory);
        if (!refFasta.exists())
        {
            throw new PipelineJobException("Error: reference file does not exist: " + refFasta.getPath());
        }

        try
        {
            File movedSharedDir = _wd.inputFile(sharedDirectory, true);
            File movedFasta = new File(movedSharedDir, refFasta.getName());

            RecordedAction action = new RecordedAction(COPY_REFERENCE_LIBRARY_ACTIONNAME);
            action.addInput(refFasta, "Initial FASTA");
            action.addOutput(movedFasta, "Temportary FASTA", true);
            actions.add(action);

            return movedFasta;
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

    public static Map<ReadsetModel, Pair<File, File>> getAlignmentFiles(PipelineJob job, List<File> inputFiles, boolean returnOutputName)
    {
        HashMap<String, File> map = new HashMap<>();
        for (File f : inputFiles)
        {
            map.put(f.getName(), f);
        }

        //this iterates over each incoming readset and groups paired end reads, if applicable.
        Map<ReadsetModel, Pair<File, File>> alignFiles = new HashMap<>();

        SequencePipelineSettings settings = new SequencePipelineSettings(job.getParameters());
        for (ReadsetModel r : settings.getReadsets())
        {
            String fn = r.getFileName();
            if (returnOutputName)
                fn = SequenceTaskHelper.getExpectedNameForInput(fn);

            if (StringUtils.isEmpty(fn))
                continue;

            if (map.get(fn) == null)
            {
                //note: this task can be split into multiple jobs, meaning a given job could lack one of the expected files
                continue;
            }
            Pair<File, File> pair = new Pair<>(map.get(fn), null);

            String fn2 = r.getFileName2();
            if (returnOutputName)
                fn2 = SequenceTaskHelper.getExpectedNameForInput(fn2);

            if (!StringUtils.isEmpty(fn2))
            {
                if (map.get(fn2) == null)
                {
                    //job.getLogger().error("Unable to find expected file: " + fn2);
                }
                else
                {
                    pair.second = map.get(fn2);
                }
            }

            alignFiles.put(r, pair);
        }

        return alignFiles;
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
            //log read count:
            Integer previousCount1 = FastqUtils.getSequenceCount(inputFile1);
            Integer previousCount2 = null;
            getJob().getLogger().info("\t" + inputFile1.getName() + ": " + previousCount1 + " sequences");

            if (inputFile2 != null)
            {
                previousCount2 = FastqUtils.getSequenceCount(inputFile2);
                getJob().getLogger().info("\t" + inputFile2.getName() + ": " + previousCount2 + " sequences");
            }

            File outputDir = new File(getHelper().getWorkingDirectory(), SequenceTaskHelper.getMinimalBaseName(inputFile1));
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
                getHelper().getFileManager().addInput(action, "Input FASTQ", pair.first);
                if (inputFile2 != null)
                {
                    getHelper().getFileManager().addInput(action, "Input FASTQ", pair.second);
                }

                PreprocessingStep step = provider.create(getHelper());
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
                getJob().getLogger().info("\t" + pair.first.getName() + ": " + count1 + " sequences" + (previousCount1 != null ? ", difference: " + (previousCount1 - count1) : ""));

                if (pair.second != null)
                {
                    int count2 = FastqUtils.getSequenceCount(pair.second);
                    getJob().getLogger().info("\t" + pair.second.getName() + ": " + count2 + " sequences" + (previousCount2 != null ? ", difference: " + (previousCount2 - count2) : ""));
                }

                //TODO: read count / metrics

                actions.add(action);
            }

            //TODO: normalize names

            return pair;
        }
    }

    private void alignSet(ReadsetModel rs, String basename, List<Pair<File, File>> files, List<RecordedAction> actions, File refDB) throws IOException, PipelineJobException
    {
        getJob().setStatus(ALIGNMENT_STATUS);
        getJob().getLogger().info("Beginning to align files for: " +  basename);

        for (Pair<File, File> pair : files)
        {
            getJob().getLogger().info("Aligning inputs: " + pair.first.getName() + (pair.second == null ? "" : " and " + pair.second.getName()));

            //TODO: metrics
            //log input sequence count
            getJob().getLogger().info("\t" + pair.first.getName() + ": " + FastqUtils.getSequenceCount(pair.first) + " sequences");

            if (pair.second != null)
            {
                getJob().getLogger().info("\t" + pair.second.getName() + ": " + FastqUtils.getSequenceCount(pair.second) + " sequences");
            }

            doAlignmentForPair(actions, rs, basename, pair, refDB);
        }
    }

    public void doAlignmentForPair(List<RecordedAction> actions, ReadsetModel rs, String outputBaseName, Pair<File, File> inputFiles, File referenceFasta) throws PipelineJobException, IOException
    {
        RecordedAction alignmentAction = new RecordedAction(ALIGNMENT_ACTIONNAME);
        getHelper().getFileManager().addInput(alignmentAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, inputFiles.first);

        if (inputFiles.second != null)
        {
            getHelper().getFileManager().addInput(alignmentAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, inputFiles.second);
        }

        getHelper().getFileManager().addInput(alignmentAction, ReferenceLibraryTask.REFERENCE_DB_FASTA, referenceFasta);
        getJob().getLogger().info("Beginning alignment for: " + inputFiles.first.getName() + (inputFiles.second == null ? "" : " and " + inputFiles.second.getName()));

        File outputDirectory = new File(getHelper().getWorkingDirectory(), SequenceTaskHelper.getMinimalBaseName(inputFiles.first));
        outputDirectory = new File(outputDirectory, ALIGNMENT_SUBFOLDER_NAME);
        if (!outputDirectory.exists())
        {
            getJob().getLogger().debug("creating directory: " + outputDirectory.getPath());
            outputDirectory.mkdirs();
        }

        AlignmentStep alignmentStep = getHelper().getSingleStep(AlignmentStep.class).create(getHelper());
        AlignmentStep.AlignmentOutput alignmentOutput = alignmentStep.performAlignment(inputFiles.first, inputFiles.second, outputDirectory, referenceFasta, SequenceTaskHelper.getMinimalBaseName(inputFiles.first) + "." + alignmentStep.getProvider().getName().toLowerCase());
        getHelper().getFileManager().addStepOutputs(alignmentAction, alignmentOutput);

        if (alignmentOutput.getBAM() == null || !alignmentOutput.getBAM().exists())
        {
            throw new PipelineJobException("Unable to find BAM file after alignment");
        }
        getHelper().getFileManager().addIntermediateFile(alignmentOutput.getBAM());
        actions.add(alignmentAction);

        //sort
        File bam = alignmentOutput.getBAM();
        getJob().getLogger().info("***Sorting BAM: " + bam.getPath());
        RecordedAction sortAction = new RecordedAction(SORT_BAM_ACTION);
        getHelper().getFileManager().addInput(sortAction, "Input BAM", bam);

        SortSamStep sortStep = new SortSamStep.Provider().create(getHelper());
        BamProcessingStep.Output sortOutput = sortStep.processBam(rs, bam, referenceFasta, bam.getParentFile());
        getHelper().getFileManager().addStepOutputs(sortAction, sortOutput);
        bam = sortOutput.getBAM();
        getHelper().getFileManager().addOutput(sortAction, "Sorted BAM", bam);

        actions.add(sortAction);

        //post-processing
        List<PipelineStepProvider<BamProcessingStep>> providers = SequencePipelineService.get().getSteps(getJob(), BamProcessingStep.class);
        if (providers.isEmpty())
        {
            getJob().getLogger().info("No BAM postprocessing is necessary");
        }
        else
        {
            getJob().getLogger().info("***Starting BAM Post processing");
            for (PipelineStepProvider<BamProcessingStep> provider : providers)
            {
                getJob().getLogger().info("performing step: " + provider.getLabel());
                RecordedAction action = new RecordedAction(provider.getLabel());
                getHelper().getFileManager().addInput(action, "Input BAM", bam);

                BamProcessingStep step = provider.create(getHelper());
                BamProcessingStep.Output output = step.processBam(rs, bam, referenceFasta, bam.getParentFile());
                getHelper().getFileManager().addStepOutputs(action, output);
                actions.add(action);

                bam = output.getBAM();
                getJob().getLogger().info("\ttotal alignments in processed BAM: " + SequenceUtil.getAlignmentCount(bam));
            }
        }

        //then index
        RecordedAction indexAction = new RecordedAction(INDEX_ACTION);
        getHelper().getFileManager().addInput(indexAction, "Input BAM", bam);
        File finalBam = new File(bam.getParentFile(), outputBaseName + ".bam");
        if (!finalBam.getPath().equals(bam.getPath()))
        {
            getJob().getLogger().info("\tnormalizing BAM name to: " + finalBam.getPath());
            FileUtils.moveFile(bam, finalBam);
        }
        getHelper().getFileManager().addOutput(indexAction, FINAL_BAM_ROLE, finalBam);

        try (SAMFileReader reader = new SAMFileReader(finalBam))
        {
            getJob().getLogger().info("\tcreating BAM index");
            reader.setValidationStringency(SAMFileReader.ValidationStringency.SILENT);
            File bai = new File(finalBam.getPath() + ".bai");
            BuildBamIndex.createIndex(reader, bai);
            getHelper().getFileManager().addOutput(indexAction, FINAL_BAM_INDEX_ROLE, bai);
        }
        getJob().getLogger().info("\ttotal alignments in final BAM: " + SequenceUtil.getAlignmentCount(finalBam));

        actions.add(indexAction);
    }
}

