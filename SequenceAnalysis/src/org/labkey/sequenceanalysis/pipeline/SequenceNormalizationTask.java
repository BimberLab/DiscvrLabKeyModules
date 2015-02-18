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

import au.com.bytecode.opencsv.CSVReader;
import htsjdk.samtools.util.FastqQualityFormat;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
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
import org.labkey.api.sequenceanalysis.model.ReadsetModel;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.run.preprocessing.SeqCrumbsRunner;
import org.labkey.sequenceanalysis.run.util.SFFExtractRunner;
import org.labkey.sequenceanalysis.util.Barcoder;
import org.labkey.sequenceanalysis.util.FastaToFastqConverter;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.NucleotideSequenceFileType;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
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
    private static final String BARCODE_ACTIONNAME = "Separate By Barcode";
    private static final String COMPRESS_ACTIONNAME = "Compressing Outputs";
    private static final String RELOCATE_ACTIONNAME = "Processing Input Files";

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
            try
            {
                boolean isParticipant = getFilesToNormalize(job, ((FileAnalysisJobSupport) job).getInputFiles(), true).size() > 0;
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
            return Arrays.asList(DECOMPRESS_ACTIONNAME, PREPARE_INPUT_ACTIONNAME, BARCODE_ACTIONNAME, COMPRESS_ACTIONNAME);
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

    public static List<File> getFilesToNormalize(PipelineJob job, List<File> files) throws FileNotFoundException
    {
        return getFilesToNormalize(job, files, false);
    }

    private static List<File> getFilesToNormalize(PipelineJob job, List<File> files, boolean allowMissingFiles) throws FileNotFoundException
    {
        SequencePipelineSettings settings = new SequencePipelineSettings(job.getParameters());
        if (settings.isDoBarcode())
        {
            return files;
        }

        List<File> toNormalize = new ArrayList<>();
        for (File f : files)
        {
            if (!f.exists())
            {
                if (allowMissingFiles)
                    continue;

                throw new FileNotFoundException("Missing file: " + f.getPath());
            }

            if (isNormalizationRequired(job, f))
                toNormalize.add(f);
        }

        return toNormalize;
    }

    private static boolean isNormalizationRequired(PipelineJob job, File f)
    {
        SequencePipelineSettings settings = new SequencePipelineSettings(job.getParameters());
        if (settings.isDoBarcode())
        {
            return true;
        }

        if (FastqUtils.FqFileType.isType(f))
        {
            if (!FastqQualityFormat.Standard.equals(FastqUtils.inferFastqEncoding(f)))
            {
                job.getLogger().debug("fastq file does not appear to use standard encoding (ASCII 33): " + f.getPath());
                return true;
            }
            else
            {
                job.getLogger().debug("normalization not required: " + f.getPath());
                return false;
            }
        }
        else
        {
            return true;
        }
    }

    private SequenceTaskHelper getHelper()
    {
        return _taskHelper;
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        _taskHelper = new SequenceTaskHelper(getJob(), _wd);
        _actions = new ArrayList<>();

        getJob().getLogger().info("Starting Normalization Task");
        File normalizationDir = new File(_wd.getDir(), SequenceTaskHelper.NORMALIZATION_SUBFOLDER_NAME);
        normalizationDir.mkdirs();

        try
        {
            List<File> inputs = getHelper().getSupport().getInputFiles();
            List<File> inputFiles = getFilesToNormalize(getJob(), inputs);
            if (inputFiles.size() == 0)
            {
                getJob().getLogger().info("There are no files to be normalized");
                return new RecordedActionSet(_actions);
            }

            //files not needing normalization
            List<File> normalizationNotNeeded = new ArrayList<>(getHelper().getSupport().getInputFiles());
            normalizationNotNeeded.removeAll(inputFiles);
            for (File f : normalizationNotNeeded)
            {
                getJob().getLogger().info("normalization not required: " + f.getPath());
                getHelper().getFileManager().addFinalOutputFile(f);
            }

            //decompress any files, if needed
            RecordedAction action = new RecordedAction(DECOMPRESS_ACTIONNAME);
            action.setStartTime(new Date());
            List<Pair<File, File>> unzippedMap = new ArrayList<>();
            FileType gz = new FileType(".gz");
            for (File i : inputFiles)
            {
                //NOTE: because we can initate runs on readsets from different containers, we cannot rely on dataDirectory() to be consistent
                //b/c inputs are always copied to the root of the analysis folder, we will use relative paths
                if (gz.isType(i))
                {
                    String size = FileUtils.byteCountToDisplaySize(FileUtils.sizeOfAsBigInteger(i));
                    getJob().getLogger().debug("\tDecompressing file: " + i.getName() + " (" + size + ")");

                    File unzipped = new File(_wd.getDir(), i.getName().replaceAll(".gz$", ""));
                    unzipped = Compress.decompressGzip(i, unzipped);
                    unzippedMap.add(Pair.of(i, unzipped));
                    action.addInput(i, "Compressed File");
                    getHelper().getFileManager().addOutput(action, "Decompressed File", unzipped);
                    action.addOutput(unzipped, "Decompressed File", true);
                }
            }

            if (unzippedMap.size() > 0)
            {
                //swap unzipped files
                for (Pair<File, File> p : unzippedMap)
                {
                    inputFiles.remove(p.first);
                    inputFiles.add(p.second);
                }
            }

            action.setEndTime(new Date());
            _actions.add(action);

            Map<String, File> fileMap = new HashMap<>();
            for (File f : inputFiles)
            {
                fileMap.put(f.getName(), f);
            }

            Set<Pair<File, File>> toNormalize = new HashSet<>();
            for (ReadsetModel m : getHelper().getSettings().getReadsets())
            {
                File file1 = fileMap.get(SequenceTaskHelper.getExpectedNameForInput(m.getFileName()));
                if (file1 == null)
                {
                    throw new PipelineJobException("Unable to find input file with name: " + SequenceTaskHelper.getExpectedNameForInput(m.getFileName()));
                }

                Pair<File, File> pair = Pair.of(normalizeSequence(file1), null);

                if (!StringUtils.isEmpty(m.getFileName2()))
                {
                    File file2 = fileMap.get(SequenceTaskHelper.getExpectedNameForInput(m.getFileName2()));
                    if (file2 == null)
                    {
                        throw new PipelineJobException("Unable to find input file with name: " + SequenceTaskHelper.getExpectedNameForInput(m.getFileName2()));
                    }

                    pair.second = normalizeSequence(file2);
                }

                toNormalize.add(pair);
            }

            //barcode, if needed
            if (getHelper().getSettings().isDoBarcode())
            {
                getJob().getLogger().info("Separating reads by barcode");

                Barcoder barcoder = new Barcoder(getJob().getLogger());

                if (getMismatchesTolerated() != null)
                {
                    barcoder.setEditDistance(getMismatchesTolerated());
                }

                if (getBarcodeOffset() != null)
                {
                    barcoder.setOffsetDistance(getBarcodeOffset());
                }

                if (getDeletionsTolerated() != null)
                {
                    barcoder.setDeletionsAllowed(getDeletionsTolerated());
                }

                barcoder.setCreateSummaryLog(true);

                if (getHelper().getSettings().isDebugMode())
                    barcoder.setCreateDetailedLog(true);

                if (getBarcodeGroupsToScan(getHelper().getSettings()).size() > 0)
                    barcoder.setScanAll(true);

                List<BarcodeModel> models = getHelper().getSettings().getBarcodes();
                models.addAll(getExtraBarcodesFromFile());

                for (Pair<File, File> pair : toNormalize)
                {
                    RecordedAction barcodeAction = new RecordedAction(BARCODE_ACTIONNAME);
                    barcodeAction.setStartTime(new Date());

                    getJob().getLogger().info("demultiplexing: " + pair.first.getPath());
                    if (pair.second != null)
                    {
                        getJob().getLogger().info("and: " + pair.second.getPath());
                    }

                    getHelper().getFileManager().addInput(barcodeAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, pair.first);
                    if (pair.second != null)
                    {
                        getHelper().getFileManager().addInput(barcodeAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, pair.second);
                    }

                    File outputDir = new File(normalizationDir, FileUtil.getBaseName(pair.first));
                    outputDir.mkdirs();

                    Set<File> outputs;
                    if (pair.second == null)
                    {
                        outputs = barcoder.demultiplexFile(pair.first, getHelper().getSettings().getReadsets(), models, outputDir);
                        for (File output : outputs)
                        {
                            getHelper().getFileManager().addOutput(barcodeAction, SequenceTaskHelper.BARCODED_FASTQ_OUTPUTNAME, output);
                        }
                    }
                    else
                    {
                        outputs = barcoder.demultiplexPair(pair, getHelper().getSettings().getReadsets(), models, outputDir);
                        for (File output : outputs)
                        {
                            getHelper().getFileManager().addOutput(barcodeAction, SequenceTaskHelper.BARCODED_FASTQ_OUTPUTNAME, output);
                        }
                    }

                    File detailLog = barcoder.getDetailedLogFile();
                    if (detailLog != null && detailLog.exists())
                    {
                        File detailLogCompressed = Compress.compressGzip(detailLog);
                        getHelper().getFileManager().addOutput(barcodeAction, "Barcode Log", detailLogCompressed);
                        detailLog.delete();
                    }

                    File summaryLog = barcoder.getSummaryLogFile();
                    if (summaryLog != null && summaryLog.exists())
                    {
                        File summaryLogCompressed = Compress.compressGzip(summaryLog);
                        getHelper().getFileManager().addOutput(barcodeAction, "Barcode Log", summaryLogCompressed);
                        summaryLog.delete();
                    }

                    if (outputs.size() > 0)
                    {
                        getHelper().getFileManager().addIntermediateFile(pair.first);
                        if (pair.second != null)
                        {
                            getHelper().getFileManager().addIntermediateFile(pair.second);
                        }
                    }

                    getHelper().getFileManager().addFinalOutputFiles(outputs);

                    barcodeAction.setEndTime(new Date());
                    _actions.add(barcodeAction);
                }
            }
            else
            {
                for (Pair<File, File> pair : toNormalize)
                {
                    getHelper().getFileManager().addFinalOutputFile(pair.first);
                    if (pair.second != null)
                    {
                        getHelper().getFileManager().addFinalOutputFile(pair.second);
                    }
                }
            }

            //add final action for later tracking based on role
            File baseDirectory = new File(_wd.getDir(), SequenceTaskHelper.NORMALIZATION_SUBFOLDER_NAME);
            baseDirectory.mkdirs();

            Set<String> inputPaths = new HashSet<>();
            for (File input : getHelper().getSupport().getInputFiles())
            {
                inputPaths.add(input.getPath());
            }

            RecordedAction finalAction = new RecordedAction(COMPRESS_ACTIONNAME);
            finalAction.setStartTime(new Date());
            for (File f : getHelper().getFileManager().getFinalOutputFiles())
            {
                if (gz.isType(f))
                {
                    getJob().getLogger().info("File is already compressed: " + f.getPath());
                    getHelper().getFileManager().addOutput(finalAction, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, f);
                }
                else
                {
                    getHelper().getFileManager().addInput(finalAction, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, f);
                    File output = new File(baseDirectory, f.getName() + ".gz");
                    getJob().getLogger().info("Compressing output file: " + f.getPath());
                    Compress.compressGzip(f, output);
                    if (!inputPaths.contains(f.getPath()))
                    {
                        getJob().getLogger().info("\tDeleting uncompressed file: " + f.getName());
                        if (f.exists())
                        {
                            f.delete();
                        }
                        else
                        {
                            getJob().getLogger().error("unable to find file: " + f.getPath());
                        }
                    }
                    getHelper().getFileManager().addOutput(finalAction, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, output);
                }
            }
            finalAction.setEndTime(new Date());
            _actions.add(finalAction);

            //remove unzipped files
            job.getLogger().info("Removing unzipped inputs");
            for (Pair<File, File> z : unzippedMap)
            {
                getJob().getLogger().debug("\tRemoving unzipped file: " + z.getValue().getName());
                z.getValue().delete();
            }

            getHelper().getFileManager().handleInputs();
            getHelper().getFileManager().cleanup();
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
        action.setStartTime(new Date());

        getJob().getLogger().info("Normalizing to FASTQ: " + input.getName());

        getHelper().getFileManager().addInput(action, getHelper().SEQUENCE_DATA_INPUT_NAME, input);
        String basename = SequenceTaskHelper.getUnzippedBaseName(input.getName());

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
            FastqQualityFormat encoding = FastqUtils.inferFastqEncoding(input);
            if (encoding == null)
            {
                throw new PipelineJobException("Unable to infer FASTQ encoding for file: " + input.getPath());
            }

            if (encoding == FastqQualityFormat.Illumina || encoding == FastqQualityFormat.Solexa)
            {
                getJob().getLogger().info("Converting Illumina/Solexa FASTQ (ASCII 64) to standard encoding (ASCII 33)");
                SeqCrumbsRunner runner = new SeqCrumbsRunner(getJob().getLogger());
                output = new File(workingDir, SequenceTaskHelper.getUnzippedBaseName(input.getName()) + ".fastq");
                runner.convertFormat(input, output);
            }
            else
            {
                output = input;
            }
        }
        else if (type.equals(SequenceUtil.FILETYPE.fasta))
        {
            getJob().getLogger().info("Converting FASTA file to FASTQ");
            FastaToFastqConverter converter = new FastaToFastqConverter(getJob().getLogger());
            output = new File(workingDir, SequenceTaskHelper.getUnzippedBaseName(input.getName()) + ".fastq");
            converter.execute(input, output);
        }
        else if (type.equals(SequenceUtil.FILETYPE.sff))
        {
            SFFExtractRunner sff = new SFFExtractRunner(getJob().getLogger());
            output = new File(workingDir, SequenceTaskHelper.getUnzippedBaseName(input.getName()) + ".fastq");
            sff.convert(input, output);
        }
        else
        {
            getJob().getLogger().error("Unknown file type: " + type);
            return null;
        }

        getHelper().getFileManager().addOutput(action, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, output);

        //we expect each input file to create a subfolder of the same name.
        File directory = new File(baseDirectory, basename);
        if (directory.exists())
            getHelper().getFileManager().addOutput(action, "Normalization Files", directory);

        action.setEndTime(new Date());
        _actions.add(action);

        if (!input.getPath().equals(output.getPath()))
        {
            if (getHelper().getSettings().isDoBarcode())
            {
                getHelper().getFileManager().addIntermediateFile(input);
            }
        }

        return output;
    }

    @NotNull
    public static List<String> getBarcodeGroupsToScan(SequencePipelineSettings settings)
    {
        String json = settings.getParams().get("inputfile.barcodeGroups");
        if (json == null)
            return Collections.emptyList();

        JSONArray array = new JSONArray(json);
        List<String> ret = new ArrayList<>();
        for (Object o : array.toArray())
        {
            ret.add((String)o);
        }

        return ret;
    }

    private Integer getMismatchesTolerated()
    {
        return getHelper().getSettings().getParams().containsKey("inputfile.barcodeEditDistance") ? Integer.parseInt(getHelper().getSettings().getParams().get("inputfile.barcodeEditDistance")) : null;
    }

    private Integer getDeletionsTolerated()
    {
        return getHelper().getSettings().getParams().containsKey("inputfile.barcodeDeletions") ? Integer.parseInt(getHelper().getSettings().getParams().get("inputfile.barcodeDeletions")) : null;
    }

    private Integer getBarcodeOffset()
    {
        return getHelper().getSettings().getParams().containsKey("inputfile.barcodeOffset") ? Integer.parseInt(getHelper().getSettings().getParams().get("inputfile.barcodeOffset")) : null;
    }

    public List<BarcodeModel> getExtraBarcodesFromFile() throws PipelineJobException
    {
        List<BarcodeModel> models = new ArrayList<>();
        File barcodes = ReadsetImportTask.getExtraBarcodesFile(getHelper());

        if (barcodes.exists())
        {
            getJob().getLogger().debug("\tReading additional barcodes from file");

            CSVReader reader = null;

            try
            {
                reader = new CSVReader(new FileReader(barcodes), '\t');
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    BarcodeModel model = new BarcodeModel();
                    if (!StringUtils.isEmpty(line[0]))
                    {
                        model.setName(line[0]);
                        model.setSequence(line[1]);
                        models.add(model);
                    }
                }
            }
            catch (FileNotFoundException e)
            {
                throw new PipelineJobException(e);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            finally
            {
                try
                {
                    if (reader != null)
                        reader.close();

                    barcodes.delete();
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

        return models;
    }
}
