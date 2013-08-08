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

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.run.SFFExtractRunner;
import org.labkey.sequenceanalysis.run.SeqCrumbsRunner;
import org.labkey.sequenceanalysis.util.Barcoder;
import org.labkey.sequenceanalysis.util.FastaToFastqConverter;
import org.labkey.sequenceanalysis.util.FastqMerger;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.NucleotideSequenceFileType;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 4/21/12
 * Time: 3:37 PM
 */
public class SequenceNormalizationTask extends WorkDirectoryTask<SequenceNormalizationTask.Factory>
{
    private SequenceTaskHelper _taskHelper;
    private static final String PREPARE_INPUT_ACTIONNAME = "Converting to FASTQ";
    private static final String PREPARE_INPUT_STATUS = "NORMALIZING INPUT FILES";
    private static final String DECOMPRESS_ACTIONNAME = "Decompressing Inputs";
    private static final String MERGE_ACTIONNAME = "Merging FASTQ File";
    private static final String BARCODE_ACTIONNAME = "Separate By Barcode";
    private static final String COMPRESS_ACTIONNAME = "Compressing Outputs";
    private static final String RELOCATE_ACTIONNAME = "Processing Input Files";

    private Set<File> _unalteredInputs = new HashSet<>();

    private List<RecordedAction> _actions;

    private SequenceNormalizationTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(SequenceNormalizationTask.class);
            setJoin(true);
        }

        @Override
        public boolean isParticipant(PipelineJob job)
        {
            SequenceTaskHelper taskHelper = new SequenceTaskHelper(job);

            try
            {
                boolean isParticipant = taskHelper.getFilesToNormalize(((FileAnalysisJobSupport) job).getInputFiles(), true).size() > 0;
                if (!isParticipant)
                    job.getLogger().debug("Skipping sequence normalization step on remove server since there are no files to normalize");

                return isParticipant;
            }
            catch (FileNotFoundException e)
            {
                //this should never occur, since the above flag allows missing files
            }

            return true;
        }

        public String getStatusName()
        {
            return PREPARE_INPUT_STATUS;
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(DECOMPRESS_ACTIONNAME, PREPARE_INPUT_ACTIONNAME, MERGE_ACTIONNAME, BARCODE_ACTIONNAME, COMPRESS_ACTIONNAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            SequenceNormalizationTask task = new SequenceNormalizationTask(this, job);
            setJoin(true);

            return task;
        }

        public List<FileType> getInputTypes()
        {
            return Collections.singletonList((FileType)new NucleotideSequenceFileType());
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private SequenceTaskHelper getHelper()
    {
        return _taskHelper;
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        _taskHelper = new SequenceTaskHelper(getJob(), _wd.getDir());
        _actions = new ArrayList<>();

        getJob().getLogger().info("Starting Normalization Task");
        File normalizationDir = new File(_wd.getDir(), SequenceTaskHelper.NORMALIZATION_SUBFOLDER_NAME);
        normalizationDir.mkdirs();

        try
        {
            File fileWorkInputXML = new File(getHelper().getSupport().getAnalysisDirectory(), "sequencePipeline.xml");
            getHelper().getSupport().createParamParser().writeFromMap(getHelper().getSettings().getParams(), fileWorkInputXML);
            getJob().getActionSet().add(fileWorkInputXML, "Pipeline Parameters");

            fileWorkInputXML = new File(_wd.getDir(), "sequencePipeline.xml");
            getHelper().getSupport().createParamParser().writeFromMap(getHelper().getSettings().getParams(), fileWorkInputXML);

            List<File> inputs = getHelper().getSupport().getInputFiles();
            List<File> inputFiles = getHelper().getFilesToNormalize(inputs);

            if(inputFiles.size() == 0)
            {
                getJob().getLogger().info("There are no files to normalized");
                return new RecordedActionSet(_actions);
            }

            //decompress any files, if needed
            RecordedAction action = new RecordedAction(DECOMPRESS_ACTIONNAME);
            List<Pair<File, File>> unzippedMap = new ArrayList<>();
            FileType gz = new FileType(".gz");
            for(File i : inputFiles)
            {
                //NOTE: because we can initate runs on readsets from different containers, we cannot rely on dataDirectory() to be consistent
                //b/c inputs are always copied to the root of the analysis folder, we will use relative paths
                if(gz.isType(i))
                {
                    getJob().getLogger().debug("\tDecompressing file: " + i.getName());

                    File unzipped = new File(_wd.getDir(), i.getName().replaceAll(".gz$", ""));
                    unzipped = Compress.decompressGzip(i, unzipped);

                    unzippedMap.add(Pair.of(i, unzipped));
                    action.addInput(i, "Compressed File");
                    getHelper().addOutput(action, "Decompressed File", unzipped);
                    action.addOutput(unzipped, "Decompressed File", true);
                }
            }

            if(unzippedMap.size() > 0)
            {
                //swap unzipped files
                for (Pair<File, File> p : unzippedMap)
                {
                    inputFiles.remove(p.first);
                    inputFiles.add(p.second);
                }
            }
            _actions.add(action);

            List<File> normalizedFiles = new ArrayList<>();
            for (File f : inputFiles)
            {
                normalizedFiles.add(normalizeSequence(f));
            }

            //merge if needed
            List<File> postMerge = new ArrayList<>();
            RecordedAction mergeAction = new RecordedAction(MERGE_ACTIONNAME);
            if (getHelper().getSettings().isDoMerge())
            {
                getJob().getLogger().info("Merging sequence files");
                for (File f : normalizedFiles)
                {
                    getHelper().addInput(mergeAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, f);
                }


                FastqMerger merger = new FastqMerger(getJob().getLogger());
                File output = new File(normalizationDir, getHelper().getSettings().getMergeFilename());
                merger.mergeFiles(output, normalizedFiles);
                getHelper().addOutput(mergeAction, SequenceTaskHelper.MERGED_FASTQ_OUTPUTNAME, output);

                if (output.exists())
                {
                    for (File f : normalizedFiles)
                    {
                        getHelper().addIntermediateFile(f);
                    }
                }

                postMerge.add(output);
            }
            else
            {
                postMerge.addAll(normalizedFiles);
            }
            _actions.add(mergeAction);

            //barcode, if needed
            RecordedAction barcodeAction = new RecordedAction(BARCODE_ACTIONNAME);
            if (getHelper().getSettings().isDoBarcode())
            {
                getJob().getLogger().info("Separating reads by barcode");

                Barcoder barcoder = new Barcoder(getJob().getLogger());
                barcoder.setEditDistance(getHelper().getSettings().getBarcodeMismatches());
                barcoder.setOffsetDistance(getHelper().getSettings().getBarcodeOffset());
                barcoder.setDeletionsAllowed(getHelper().getSettings().getBarcodeDeletions());
                barcoder.setCreateSummaryLog(true);

                if (getHelper().getSettings().isDebugMode())
                    barcoder.setCreateDetailedLog(true);

                if (getHelper().getSettings().getBarcodeGroupsToScan().size() > 0)
                    barcoder.setScanAll(true);

                List<BarcodeModel> models = getHelper().getSettings().getBarcodes();
                models.addAll(getHelper().getExtraBarcodesFromFile());

                for (File input : postMerge)
                {
                    getHelper().addInput(barcodeAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, input);
                    File outputDir = new File(normalizationDir, FileUtil.getBaseName(input));
                    outputDir.mkdirs();

                    Set<File> outputs = barcoder.execute(input, getHelper().getSettings().getReadsets(), models, outputDir);
                    for (File output : outputs)
                    {
                        getHelper().addOutput(barcodeAction, SequenceTaskHelper.BARCODED_FASTQ_OUTPUTNAME, output);
                    }

                    File detailLog = barcoder.getDetailedLogFile();
                    if (detailLog != null && detailLog.exists())
                    {
                        File detailLogCompressed = Compress.compressGzip(detailLog);
                        getHelper().addOutput(barcodeAction, "Barcode Log", detailLogCompressed);
                        detailLog.delete();
                    }

                    File summaryLog = barcoder.getSummaryLogFile();
                    if (summaryLog != null && summaryLog.exists())
                    {
                        File summaryLogCompressed = Compress.compressGzip(summaryLog);
                        getHelper().addOutput(barcodeAction, "Barcode Log", summaryLogCompressed);
                        summaryLog.delete();
                    }

                    if (outputs.size() > 0)
                    {
                        getHelper().addIntermediateFile(input);
                    }

                    getHelper().addFinalOutputFiles(outputs);
                }
            }
            else
            {
                getHelper().addFinalOutputFiles(postMerge);
            }
            _actions.add(barcodeAction);

            //add final action for later tracking based on role
            File baseDirectory = new File(_wd.getDir(), SequenceTaskHelper.NORMALIZATION_SUBFOLDER_NAME);
            baseDirectory.mkdirs();

            Set<String> inputPaths = new HashSet<>();
            for (File input : getHelper().getSupport().getInputFiles())
            {
                inputPaths.add(input.getPath());
            }

            RecordedAction finalAction = new RecordedAction(COMPRESS_ACTIONNAME);
            for (File f : getHelper().getFinalOutputfiles())
            {
                getHelper().addInput(finalAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, f);
                File output = new File(baseDirectory, f.getName() + ".gz");
                getJob().getLogger().info("Compressing output file: " + f.getName());
                Compress.compressGzip(f, output);
                if (!inputPaths.contains(f.getPath()))
                {
                    getJob().getLogger().info("\tDeleting uncompressed file: " + f.getName());
                    f.delete();
                }
                getHelper().addOutput(finalAction, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, output);
            }
            _actions.add(finalAction);

            //remove unzipped files
            job.getLogger().info("Removing unzipped inputs");
            for(Pair<File, File> z : unzippedMap)
            {
                getJob().getLogger().debug("\tRemoving unzipped file: " + z.getValue().getName());
                z.getValue().delete();
            }

            getHelper().handleInputs(_wd);
            getHelper().cleanup(_wd);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        getJob().getLogger().debug("This job created " + _actions.size() + " actions:");
        for (RecordedAction a : _actions)
        {
            getJob().getLogger().debug("\t" + a.getName());
        }

        return new RecordedActionSet(_actions);
    }

    private File normalizeSequence(File input) throws PipelineJobException, IOException
    {
        PipelineJob job = getJob();
        job.setStatus(PREPARE_INPUT_STATUS);

        RecordedAction action = new RecordedAction(PREPARE_INPUT_ACTIONNAME);

        getJob().getLogger().info("Normalizing to FASTQ: " + input.getName());

        getHelper().addInput(action, getHelper().SEQUENCE_DATA_INPUT_NAME, input);
        String basename = getHelper().getSettings().getBasename(input.getName());

        SequenceUtil.FILETYPE type = SequenceUtil.inferType(input);
        if (type == null)
        {
            getJob().getLogger().error("Unable to determine type for file: " + input.getPath());
            return null;
        }

        getJob().getLogger().info("\tFile format: " + type.getPrimaryExtension());

        File baseDirectory = new File(_wd.getDir(), SequenceTaskHelper.NORMALIZATION_SUBFOLDER_NAME);
        baseDirectory.mkdirs();

        File workingDir = new File(baseDirectory, basename);
        workingDir.mkdirs();

        File output = null;
        //NOTE: if the file is ASCII33 FASTQ, it should not reach this stage, unless we're doing barcoding or merging
        if (type.equals(SequenceUtil.FILETYPE.fastq))
        {
            FastqUtils.FASTQ_ENCODING encoding = FastqUtils.inferFastqEncoding(input);
            if (encoding.equals(FastqUtils.FASTQ_ENCODING.Illumina))
            {
                getJob().getLogger().info("Converting Illumina/Solexa FASTQ (ASCII 64) to standard encoding (ASCII 33)");
                SeqCrumbsRunner runner = new SeqCrumbsRunner(getJob().getLogger());
                output = new File(workingDir, SequenceTaskHelper.getMinimalBaseName(input) + ".fastq");
                runner.convertFormat(output, input);
            }
            else
            {
                output = input;
                _unalteredInputs.add(input);
            }
        }
        else if (type.equals(SequenceUtil.FILETYPE.fasta))
        {
            getJob().getLogger().info("Converting FASTA file to FASTQ");
            FastaToFastqConverter converter = new FastaToFastqConverter(getJob().getLogger());
            output = new File(workingDir, SequenceTaskHelper.getMinimalBaseName(input) + ".fastq");
            converter.execute(input, output);
        }
        else if (type.equals(SequenceUtil.FILETYPE.sff))
        {
            SFFExtractRunner sff = new SFFExtractRunner(getJob().getLogger());
            output = new File(workingDir, SequenceTaskHelper.getMinimalBaseName(input) + ".fastq");
            sff.convert(input, output);
        }

        getHelper().addOutput(action, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, output);

        //we expect each input file to create a subfolder of the same name.
        File directory = new File(baseDirectory, basename);
        if(directory.exists())
            getHelper().addOutput(action, "Normalization Files", directory);

        _actions.add(action);

        if (!input.getPath().equals(output.getPath()))
        {
            if (getHelper().getSettings().isDoBarcode() || getHelper().getSettings().isDoMerge())
            {
                getHelper().addIntermediateFile(input);
            }
        }

        return output;
    }
}
