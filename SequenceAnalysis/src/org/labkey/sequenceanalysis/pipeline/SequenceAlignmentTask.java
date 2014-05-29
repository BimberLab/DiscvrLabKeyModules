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
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.model.ReadsetModel;
import org.labkey.sequenceanalysis.run.BWARunner;
import org.labkey.sequenceanalysis.run.BowtieRunner;
import org.labkey.sequenceanalysis.run.FastaIndexer;
import org.labkey.sequenceanalysis.run.MergeSamFilesRunner;
import org.labkey.sequenceanalysis.run.MosaikRunner;
import org.labkey.sequenceanalysis.util.FastqUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final String REFERENCE_STATUS = "CREATING REFERENCE LIBRARY";

    public static final String PREPROCESS_FASTQ_ACTIONNAME = "Preprocess FASTQ";
    public static final String ALIGNMENT_ACTIONNAME = "Performing Alignment";
    public static final String MERGE_ALIGNMENT_ACTIONNAME = "Merging Alignments";
    public static final String DECOMPRESS_ACTIONNAME = "Decompressing Inputs";
    private static final String ALIGNMENT_SUBFOLDER_NAME = "Alignment";  //the subfolder within which alignment data will be placed
    private static final String BAM_INDEX = "BAM File Index";

    public static final String BAM_ROLE = "BAM File";
    public static final String PROCESSED_FQ_ROLE = "Processed FASTQ";

    Map<File, File> _unzippedMap = new HashMap<>();
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

        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(FastqUtils.FqFileType);
        }

        public String getStatusName()
        {
            return "SEQUENCE ALIGNMENT";
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(DECOMPRESS_ACTIONNAME, PREPROCESS_FASTQ_ACTIONNAME, ALIGNMENT_ACTIONNAME, MERGE_ALIGNMENT_ACTIONNAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            SequenceAlignmentTask task = new SequenceAlignmentTask(this, job);
            setJoin(false);

            return task;
        }

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

    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        _taskHelper = new SequenceTaskHelper(job, _wd.getDir());

        job.getLogger().info("Starting alignment");

        List<RecordedAction> actions = new ArrayList<>();

        try
        {
            File fileWorkInputXML = _wd.newFile("sequencePipeline.xml");
            getHelper().getSupport().createParamParser().writeFromMap(getHelper().getSettings().getParams(), fileWorkInputXML);
            _wd.inputFile(getHelper().getSupport().getParametersFile(), true);

            List<File> inputFiles = new ArrayList<>();
            inputFiles.addAll(getHelper().getSupport().getInputFiles());
            FileType gz = new FileType(".gz");

            RecordedAction action = new RecordedAction(DECOMPRESS_ACTIONNAME);
            for (File i : inputFiles)
            {
                //NOTE: because we can initate runs on readsets from different containers, we cannot rely on dataDirectory() to be consistent
                //b/c inputs are always copied to the root of the analysis folder, we will use relative paths
                File unzipped = null;
                if (gz.isType(i))
                {
                    //NOTE: we use relative paths in all cases here
                    getJob().getLogger().debug("Decompressing file: " + i.getPath());

                    unzipped = new File(_wd.getDir(), i.getName().replaceAll(".gz$", ""));
                    getJob().getLogger().debug("\tunzipped: " + unzipped.getPath());
                    unzipped = Compress.decompressGzip(i, unzipped);

                    _unzippedMap.put(i, unzipped);
                    action.addInput(i, "Compressed File");
                    action.addOutput(unzipped, "Decompressed File", true);
                }
            }

            if (_unzippedMap.size() > 0)
            {
                //swap unzipped files
                for (File f : _unzippedMap.keySet())
                {
                    inputFiles.remove(f);
                    inputFiles.add(_unzippedMap.get(f));
                }
                actions.add(action);
            }

            //then we preprocess FASTQ files, if needed
            List<Pair<File, File>> groupedFiles = getAlignmentFiles(getJob(), inputFiles, true);
            Set<File> preprocessedOutputs = new HashSet<>();

            if (groupedFiles.size() > 0)
            {
                for (Pair<File, File> pair : groupedFiles){
                    if (getHelper().getSettings().isDoPreprocess())
                    {
                        //NOTE: use relative path
                        Pair<File, File> files = preprocessFastq(pair.first, pair.second, actions);
                        if (files != null)
                        {
                            preprocessedOutputs.add(files.first);
                            if (files.second != null)
                                preprocessedOutputs.add(files.second);

                            getHelper().addIntermediateFiles(preprocessedOutputs);

                            pair = files;
                        }
                    }
                    else
                    {
                        job.getLogger().info("No preprocessing steps selected");
                    }

                    //do align
                    if (getHelper().getSettings().isDoAlignment())
                    {
                        job.getLogger().info("Preparing to perform alignment");

                        File sharedDirectory = new File(getHelper().getSupport().getAnalysisDirectory(), SequenceTaskHelper.SHARED_SUBFOLDER_NAME);
                        File refFasta = new File(sharedDirectory,  getHelper().getSettings().getRefDbFilename());
                        if (!refFasta.exists())
                        {
                            throw new PipelineJobException("Error: reference file does not exist: " + refFasta.getPath());
                        }

                        alignSet(FileUtil.getBaseName(pair.first), Arrays.asList(pair), actions, refFasta);
                    }
                    else
                    {
                        job.getLogger().info("Alignment not selected, skipping");
                    }
                }
            }

            //compress alignment inputs if created by pre-processing
            //if we are going to delete intermediates anyway, skip this
            if (!getHelper().getSettings().isDeleteIntermediateFiles() && preprocessedOutputs.size() > 0)
            {
                Set<String> inputPaths = getHelper().getInputPaths();

                getJob().getLogger().info("Compressing input FASTQ files");
                for (File file : preprocessedOutputs)
                {
                    if (!inputPaths.contains(file.getPath()))
                    {
                        getJob().getLogger().info("\tCompressing preprocessing file: " + file.getPath());
                        File zipped = Compress.compressGzip(file);
                        getHelper().swapFiles(_wd, file, zipped);
                        file.delete();
                    }
                    else
                    {
                        getJob().getLogger().debug("\tFile was an input, will not compress: " + file.getPath());
                    }
                }
            }

            //remove unzipped files
            job.getLogger().info("Removing unzipped inputs");
            for (File z : _unzippedMap.keySet())
            {
                getJob().getLogger().debug("\tremoving: " + _unzippedMap.get(z).getPath());
                getJob().getLogger().debug("\toriginal file: " + z.getPath());
                getHelper().swapFiles(_wd, _unzippedMap.get(z), z);

                if (_unzippedMap.get(z).exists())
                    _unzippedMap.get(z).delete();
            }

            getHelper().deleteIntermediateFiles();
            getHelper().cleanup(_wd);

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
//        throw new PipelineJobException("stop!");
    }

    public static List<Pair<File, File>> getAlignmentFiles(PipelineJob job, List<File> inputFiles, boolean returnOutputName)
    {
        HashMap<String, File> map = new HashMap<>();
        for (File f : inputFiles)
        {
            map.put(f.getName(), f);
        }

        //this iterates over each incoming readset and groups paired end reads, if applicable.
        List<Pair<File, File>> alignFiles = new ArrayList<>();

        SequenceTaskHelper taskHelper = new SequenceTaskHelper(job);
        for (ReadsetModel r : taskHelper.getSettings().getReadsets())
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

            alignFiles.add(pair);
        }

        return alignFiles;
    }

    /**
     * Attempt to normalize FASTQ files and perform preprocessing such as trimming.
     */
    public Pair<File, File> preprocessFastq(File inputFile, @Nullable File inputFile2, List<RecordedAction> actions) throws PipelineJobException, IOException
    {
        PipelineJob job = getJob();
        job.setStatus(PREPROCESS_FASTQ_STATUS);

        //iterate over each output created from the first action
        RecordedAction action = new RecordedAction(PREPROCESS_FASTQ_ACTIONNAME);
        getHelper().addInput(action, "Input FASTQ", inputFile);

        job.getLogger().info("Beginning preprocessing for file: " + inputFile.getName());
        if (inputFile2 != null)
            job.getLogger().info("and file: " + inputFile2.getName());

        PreprocessTask preprocessor = new PreprocessTask(job, _wd, getHelper());
        List<Pair<File, File>> outputFiles = preprocessor.processFiles(inputFile, inputFile2);

        Pair<File, File> pair;
        if (outputFiles.size() == 0)
        {
            getJob().getLogger().error("No preprocessing was necessary");
            return null;
        }
        else
        {
            pair = outputFiles.get(0);
        }

        boolean error = false;
        if (pair.first.exists())
        {
            getHelper().addOutput(action, PROCESSED_FQ_ROLE, pair.first);
            job.getLogger().info("\tAdding output file: " + _wd.getRelativePath(pair.first));
        }
        else
        {
            job.getLogger().warn("\tNo output created, expected to find: " + _wd.getRelativePath(pair.first));
            error = true;
        }

        if (pair.second != null)
        {
            if (pair.second.exists())
            {
                getHelper().addOutput(action, PROCESSED_FQ_ROLE, pair.second);
                job.getLogger().info("\tAdding output file: " + _wd.getRelativePath(pair.second));
            }
            else
            {
                job.getLogger().warn("\tNo output created, expected to find: " + _wd.getRelativePath(pair.second));
                error = true;
            }
        }

        actions.add(action);

        if (error)
        {
            getJob().getLogger().warn("there was an error during preprocessing, files will not be added");
            return null;
        }
        else
        {
            File expectedPreprocessDir = preprocessor.getPreprocessDir(inputFile);
            if (expectedPreprocessDir.exists())
            {
                getHelper().addOutput(action, "FASTQ Preprocessing Output", expectedPreprocessDir);
            }

            return pair;
        }
    }

    private SequenceTaskHelper getHelper()
    {
        return _taskHelper;
    }

    private void alignSet(String basename, List<Pair<File, File>> files, List<RecordedAction> actions, File refDB) throws IOException, PipelineJobException
    {
        PipelineJob job = getJob();
        job.setStatus(ALIGNMENT_STATUS);

        RecordedAction action = new RecordedAction(MERGE_ALIGNMENT_ACTIONNAME);
        List<File> bams = new ArrayList<>();
        job.getLogger().info("Beginning to align files for: " +  basename);

        for (Pair<File, File> pair : files)
        {
            job.getLogger().info("Aligning inputs: " + pair.first.getName() +
                (pair.second == null ? "" : " and " + pair.second.getName()));

            //append input sequence count
            int total = FastqUtils.getSequenceCount(pair.first);
            job.getLogger().info("\t" + pair.first.getName() + ": " + total + " sequences");

            if (pair.second != null)
            {
                total = FastqUtils.getSequenceCount(pair.second);
                job.getLogger().info("\t" + pair.second.getName() + ": " + total + " sequences");
            }

            List<File> alignInputs = new ArrayList<>();
            alignInputs.add(pair.getKey());
            if (pair.getValue() != null)
                alignInputs.add(pair.getValue());

            File bam = doAlignmentForPair(actions, alignInputs, refDB);

            if (bam != null)
            {
                getHelper().addInput(action, SequenceTaskHelper.BAM_INPUT_NAME, bam);
                bams.add(bam);
            }
        }

        //merge BAMs
        File outputAlignment = null;
        if (bams.size() == 0)
        {
            getJob().getLogger().info("No BAMs found");
        }
        else if (bams.size() == 1)
        {
            outputAlignment = bams.get(0);
        }
        else
        {
            getJob().getLogger().info("Preparing to merge BAM files");
            MergeSamFilesRunner merger = new MergeSamFilesRunner(getJob().getLogger());
            merger.setWorkingDir(bams.get(0).getParentFile());
            outputAlignment = merger.execute(bams, null, true);
            File bai = new File(outputAlignment.getPath() + ".bai");
            if (bai.exists())
                bai.delete();

            SAMFileReader reader = null;
            try
            {
                reader = new SAMFileReader(outputAlignment);
                BuildBamIndex.createIndex(reader, bai);
                getHelper().addOutput(action, BAM_INDEX, bai);
            }
            finally
            {
                if (reader != null)
                    reader.close();
            }

        }

        //TODO: ensure unaligned sequences present
        //AppendUnalignedToBam appender = new AppendUnalignedToBam(getJob().getLogger());
        //File finalOutput = appender.execute(outputAlignment, files);

        if (outputAlignment != null)
        {
            getJob().getLogger().debug("\tAdding output BAM file: " + _wd.getRelativePath(outputAlignment));
            getHelper().addOutput(action, BAM_ROLE, outputAlignment);
            actions.add(action);
        }
        else
        {
            getJob().getLogger().warn("Unable to find BAM");
        }
    }

    public File doAlignmentForPair(List<RecordedAction> actions, List<File> inputFiles, File referenceFasta) throws PipelineJobException, IOException
    {
        FileType bamfile = new FileType(".bam");
        File inputFile = inputFiles.get(0);

        RecordedAction action = new RecordedAction(ALIGNMENT_ACTIONNAME);
        getHelper().addInput(action, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, inputFile);

        if (inputFiles.size() > 1){
            getHelper().addInput(action, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, inputFiles.get(1));
        }

        getHelper().addInput(action, ReferenceLibraryTask.REFERENCE_DB_FASTA, referenceFasta);
        String msg = "Beginning alignment for: ";

        String sep = "";
        for (File f : inputFiles)
        {
            msg += sep + f.getName();
            sep = " and ";
        }

        getJob().getLogger().info(msg);

        List<File> files = new ArrayList<>();
        files.add(new File(getHelper().getSupport().getAnalysisDirectory(), SequenceTaskHelper.SHARED_SUBFOLDER_NAME));
        files.addAll(inputFiles);
        getHelper().runPerlScript(files, "alignment.pl", getJob());

        //find the output FASTQ
        File baseDirectory = new File(_wd.getDir(), SequenceTaskHelper.getMinimalBaseName(inputFile));
        baseDirectory = new File(baseDirectory, ALIGNMENT_SUBFOLDER_NAME);
        File outputFile = new File(baseDirectory, SequenceTaskHelper.getMinimalBaseName(inputFile) + ".bam");
        if (NetworkDrive.exists(outputFile) && bamfile.isType(outputFile))
        {
            getHelper().addOutput(action, "Temp " + BAM_ROLE, outputFile);
            getJob().getLogger().info("\tAdding initial output BAM file: " + _wd.getRelativePath(outputFile));

            File outputFileIndex = new File(baseDirectory, SequenceTaskHelper.getMinimalBaseName(inputFile) + ".bam.bai");
            if (NetworkDrive.exists(outputFileIndex))
            {
                getHelper().addOutput(action, BAM_INDEX, outputFileIndex);
            }
        }
        else
        {
            getJob().getLogger().warn("\tNo BAM file created, expected to find: " + _wd.getRelativePath(outputFile));
        }

        getHelper().addOutput(action, "Alignment Output", baseDirectory);

        actions.add(action);
        return outputFile.exists() ? outputFile : null;
    }

    public static enum ALIGNER
    {
        mosaik(){
            public void createIndex(File refFasta, Logger log) throws PipelineJobException
            {
                MosaikRunner runner = new MosaikRunner(log);
                runner.buildReference(refFasta);
            }
        },
        bwa(){
            public void createIndex(File refFasta, Logger log) throws PipelineJobException
            {
                BWARunner bwa = new BWARunner(log);
                bwa.createIndex(refFasta);
            }
        },
        bwasw(){
            public void createIndex(File refFasta, Logger log) throws PipelineJobException
            {
                BWARunner bwa = new BWARunner(log);
                bwa.createIndex(refFasta);
            }
        },
        bowtie(){
            public void createIndex(File refFasta, Logger log) throws PipelineJobException
            {
                BowtieRunner runner = new BowtieRunner(log);
                runner.buildIndex(refFasta);
            }
        },
        lastz(){
            public void createIndex(File refFasta, Logger log) throws PipelineJobException
            {
                //nothing needed
            }
        };

        abstract public void createIndex(File refFasta, Logger log) throws PipelineJobException;
    }
}

