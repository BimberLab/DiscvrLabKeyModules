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

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.FileGroup;
import org.labkey.sequenceanalysis.ReadDataImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.NucleotideSequenceFileType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This task will inspect incoming sequence files, normalize them and prepare them for import.
 * Normalization involves converting all files to FASTQ, and potentially merging or binning on barcodes
 * in order to produce one gzipped FASTQ per sample.  This task will run on the primary webserver.
 * If any normalization steps require external tools, it will create a SequenceNormalizationTask,
 * which can be configured to run on the pipeline server. Once all normalization is complete, this
 * task will insert one row into the sequence_readsets table per sample/file
 *
 * If configured, it will also collect and store sequence metrics on the FASTQ files.
 *
 * User: bbimber
 * Date: 4/23/12
 * Time: 8:48 AM
 */
public class ReadsetInitTask extends WorkDirectoryTask<ReadsetInitTask.Factory>
{
    private SequenceTaskHelper _taskHelper;

    private static final String ACTION_NAME = "IMPORTING READSET";
    private static final String COMPRESS_INPUT_ACTIONNAME = "Compressing FASTQ Files";

    protected ReadsetInitTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(ReadsetInitTask.class);

            setJoin(true);  // Do this once per file-set.
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new ReadsetInitTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new NucleotideSequenceFileType());
        }

        @Override
        public String getStatusName()
        {
            return "IMPORT READSET";
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(ACTION_NAME, COMPRESS_INPUT_ACTIONNAME);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            // No way of knowing.
            return false;
        }

        @Override
        public boolean isParticipant(PipelineJob job)
        {
            return true;
        }

    }

    private SequenceTaskHelper getHelper()
    {
        return _taskHelper;
    }

    private final Set<File> _finalOutputFiles = new HashSet<>();
    private final Set<File> _unalteredInputs = new HashSet<>();

    private ReadsetImportJob getPipelineJob()
    {
        return (ReadsetImportJob)getJob();
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        _taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);

        getJob().getLogger().info("Preparing to import sequence data");
        getJob().getLogger().info("input files:");
        for (File input : getPipelineJob().getInputFiles())
        {
            getJob().getLogger().info(input.getPath());
        }

        createExpDatasForInputs();

        List<RecordedAction> actions = new ArrayList<>();
        RecordedAction action = new RecordedAction(COMPRESS_INPUT_ACTIONNAME);
        actions.add(action);

        //NOTE: if none of the files require external normalization, we handle all processing now and skip the external step
        //however, since processing usually deletes/moves the input, we only process files now if this step will be skipped

        try
        {
            List<FileGroup> fileGroups = getHelper().getSettings().getFileGroups(getPipelineJob());
            List<SequenceReadsetImpl> readsets = getHelper().getSettings().getReadsets(getPipelineJob());

            checkForDuplicateFileNames(readsets, fileGroups);

            if (!SequenceNormalizationTask.shouldRunRemote(getJob()))
            {
                getJob().getLogger().info("No files required external normalization, processing inputs locally");
                for (FileGroup fg : fileGroups)
                {
                    for (FileGroup.FilePair fp : fg.filePairs)
                    {
                        fp.file1 = processFile(fp.file1, action);
                        fp.file2 = processFile(fp.file2, action);
                    }
                }

                for (SequenceReadsetImpl rs : readsets)
                {
                    List<ReadDataImpl> rd = new ArrayList<>();
                    for (FileGroup fg : fileGroups)
                    {
                        if (rs.getFileSetName() != null && rs.getFileSetName().equals(fg.name))
                        {
                            for (FileGroup.FilePair fp : fg.filePairs)
                            {
                                ReadDataImpl r = new ReadDataImpl();
                                r.setPlatformUnit(fp.platformUnit);
                                r.setCenterName(fp.centerName);
                                r.setFile(fp.file1, 1);
                                r.setFile(fp.file2, 2);
                                rd.add(r);
                            }

                            getJob().getLogger().debug("readset: " + rs.getName() + ", " + rd.size() + " file pairs");
                        }
                    }
                    rs.setReadData(rd);
                }

                handleInputs(getPipelineJob(), getHelper().getFileManager().getInputFileTreatment(), actions, _finalOutputFiles, _unalteredInputs);
            }
            else
            {
                getJob().getLogger().info("At least one file requires external normalization, so handling of inputs will be deferred");
            }

            if (getHelper().getSettings().isDoBarcode())
            {
                writeExtraBarcodes();
            }

            for (SequenceReadsetImpl rs : readsets)
            {
                getJob().getLogger().debug("caching readset: " + rs.getName() + " with " + rs.getReadData().size() + " files");
                getPipelineJob().getSequenceSupport().cacheReadset(rs);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        getHelper().getFileManager().cleanup(actions);

        return new RecordedActionSet(actions);
    }

    private void createExpDatasForInputs()
    {
        for (SequenceReadsetImpl rs : getHelper().getSettings().getReadsets(getPipelineJob()))
        {
            for (ReadDataImpl rd : rs.getReadDataImpl())
            {
                if (rd.getFileId1() == null && rd.getFile1() != null)
                {
                    ExpData d = getHelper().createExpData(rd.getFile1());
                    if (d != null)
                    {
                        rd.setFileId1(d.getRowId());
                    }
                }

                if (rd.getFileId2() == null && rd.getFile2() != null)
                {
                    ExpData d = getHelper().createExpData(rd.getFile2());
                    if (d != null)
                    {
                        rd.setFileId2(d.getRowId());
                    }
                }
            }
        }
    }

    private File processFile(File f, RecordedAction action) throws IOException
    {
        if (f == null)
        {
            return null;
        }

        FileType gz = new FileType(".gz");
        if (!FastqUtils.FqFileType.isType(f))
        {
            getJob().getLogger().warn("Input file is not FASTQ: " + f.getPath());
            return null;
        }

        getHelper().getFileManager().addInput(action, SequenceTaskHelper.SEQUENCE_DATA_INPUT_NAME, f);

        File output;
        if (!gz.isType(f))
        {
            getJob().getLogger().info("\tcompressing input file: " + f.getName());
            output = new File(getHelper().getJob().getAnalysisDirectory(), f.getName() + ".gz");
            //note: the non-compressed file will potentially be deleted during cleanup, depending on the selection for input handling
            Compress.compressGzip(f, output);
        }
        else
        {
            if (getHelper().getFileManager().getInputFileTreatment() == TaskFileManager.InputFileTreatment.leaveInPlace)
            {
                getJob().getLogger().debug("input files will be left in place");
                output = f;
            }
            else
            {
                output = new File(getHelper().getJob().getAnalysisDirectory(), f.getName());
                if (!output.exists())
                {
                    if (getHelper().getFileManager().getInputFileTreatment() == TaskFileManager.InputFileTreatment.delete || getHelper().getFileManager().getInputFileTreatment() == TaskFileManager.InputFileTreatment.compress)
                    {
                        getJob().getLogger().info("moving unaltered input to final location");
                        getJob().getLogger().debug(output.getPath());
                        FileUtils.moveFile(f, output);
                    }
                    else
                    {
                        getJob().getLogger().info("copying unaltered input to final location");
                        getJob().getLogger().debug(output.getPath());
                        FileUtils.copyFile(f, output);
                    }
                }
            }
        }

        getHelper().getFileManager().addOutput(action, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, output);
        _unalteredInputs.add(output); //even if compressed, it was not really changed and we shouldnt copy it back to the server
        _finalOutputFiles.add(output);

        return output;
    }

    public void writeExtraBarcodes() throws PipelineJobException
    {
        BarcodeModel[] models = getAdditionalBarcodes();
        if (models != null && models.length > 0)
        {
            File extraBarcodes = getExtraBarcodesFile(getHelper());
            getJob().getLogger().debug("\tWriting additional barcodes to file: " + extraBarcodes.getPath());
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(extraBarcodes), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                for (BarcodeModel m : models)
                {
                    writer.writeNext(new String[]{m.getName(), m.getSequence()});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    private BarcodeModel[] getAdditionalBarcodes()
    {
        if (!SequenceNormalizationTask.getBarcodeGroupsToScan(getHelper().getSettings()).isEmpty())
        {
            return BarcodeModel.getByGroups(SequenceNormalizationTask.getBarcodeGroupsToScan(getHelper().getSettings()));
        }

        return null;
    }

    public static File getExtraBarcodesFile(SequenceTaskHelper helper)
    {
        return new File(helper.getJob().getAnalysisDirectory(), "extraBarcodes.txt");
    }

    public static Set<File> handleInputs(SequenceJob job, TaskFileManager.InputFileTreatment inputFileTreatment, Collection<RecordedAction> actions, Set<File> outputFiles, @Nullable Set<File> unalteredInputs) throws PipelineJobException
    {
        Set<File> inputs = new HashSet<>();
        inputs.addAll(job.getInputFiles());

        job.getLogger().info("Cleaning up input files");

        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            throw new PipelineJobException(e);
        }

        if (TaskFileManager.InputFileTreatment.delete == inputFileTreatment)
        {
            job.getLogger().info("deleting input files");
            for (File input : inputs)
            {
                if (input.exists())
                {
                    if (!outputFiles.contains(input))
                    {
                        job.getLogger().info("Deleting input file: " + input.getPath());

                        input.delete();
                        if (input.exists())
                        {
                            throw new PipelineJobException("Unable to delete file: " + input.getPath());
                        }
                    }
                    else
                    {
                        //this input file was not altered during normalization.  in this case, we move it into the analysis folder
                        job.getLogger().info("File was not altered by normalization.  Moving to analysis folder: " + input.getPath());
                        moveInputToAnalysisDir(input, job, actions, unalteredInputs, outputFiles);
                    }
                }
            }
        }
        else if (TaskFileManager.InputFileTreatment.compress == inputFileTreatment)
        {
            job.getLogger().info("compressing input files");
            FileType gz = new FileType(".gz");
            for (File input : inputs)
            {
                if (input.exists())
                {
                    if (gz.isType(input))
                    {
                        job.getLogger().debug("Moving input file to analysis directory: " + input.getPath());
                        moveInputToAnalysisDir(input, job, actions, unalteredInputs, outputFiles);
                    }
                    else
                    {
                        job.getLogger().info("Compressing/moving input file to analysis directory: " + input.getPath());
                        File compressed = Compress.compressGzip(input);
                        if (!compressed.exists())
                            throw new PipelineJobException("Unable to compress file: " + input);

                        TaskFileManagerImpl.swapFilesInRecordedActions(job.getLogger(), input, compressed, actions, job, null);

                        input.delete();
                        if (outputFiles != null && outputFiles.contains(input))
                        {
                            job.getLogger().debug("replacing file in final outputs: " + input.getPath() + " to " + compressed.getPath());
                            outputFiles.remove(input);
                            outputFiles.add(compressed);
                        }

                        moveInputToAnalysisDir(compressed, job, actions, unalteredInputs, outputFiles);
                    }
                }
            }
        }
        else
        {
            job.getLogger().info("\tInput files will be left alone");
        }

        return outputFiles;
    }

    private static void moveInputToAnalysisDir(File input, SequenceJob job, Collection<RecordedAction> actions, @Nullable Set<File> unalteredInputs, Set<File> outputs) throws PipelineJobException
    {
        job.getLogger().debug("Moving input file to analysis directory: " + input.getPath());

        try
        {
            //NOTE: we assume the input is gzipped already
            File outputDir = job.getAnalysisDirectory();
            File output = new File(outputDir, input.getName());
            job.getLogger().debug("Destination: " + output.getPath());
            if (output.exists())
            {
                if (unalteredInputs != null && unalteredInputs.contains(output))
                {
                    job.getLogger().debug("\tThis input was unaltered during normalization and a copy already exists in the analysis folder so the original will be discarded");
                    input.delete();
                    TaskFileManagerImpl.swapFilesInRecordedActions(job.getLogger(), input, output, actions, job, null);
                    return;
                }
                else
                {
                    output = new File(outputDir, FileUtil.getBaseName(input.getName()) + ".orig.gz");
                    job.getLogger().debug("\tA file with the expected output name already exists, so the original will be renamed: " + output.getPath());
                }
            }

            FileUtils.moveFile(input, output);
            if (!output.exists())
            {
                throw new PipelineJobException("Unable to move file: " + input.getPath());
            }

            if (outputs != null)
            {
                job.getLogger().debug("swapping moved final sequence file: " + input.getPath() + " to " + output.getPath());
                if (outputs.contains(input))
                {
                    outputs.remove(input);
                    outputs.add(output);
                }
                else
                {
                    job.getLogger().debug("file was not listed as an output, do not update path: " + input.getPath());
                }
            }

            TaskFileManagerImpl.swapFilesInRecordedActions(job.getLogger(), input, output, actions, job, null);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private void checkForDuplicateFileNames(List<SequenceReadsetImpl> readsets, List<FileGroup> fileGroups) throws PipelineJobException
    {
        // check for duplicate filename between incoming and existing
        for (SequenceReadsetImpl r : readsets)
        {
            boolean readsetExists = r.getReadsetId() != null && r.getReadsetId() > 0;
            SequenceReadsetImpl existingReadset = readsetExists ? ((SequenceReadsetImpl) SequenceAnalysisService.get().getReadset(r.getReadsetId(), getJob().getUser())) : null;
            List<ReadDataImpl> preexistingReadData = readsetExists ? existingReadset.getReadDataImpl() : Collections.emptyList();
            getJob().getLogger().debug("Inspecting readset " + r.getName() + " for duplicate data. Readset already created: " + readsetExists + ", total pre-existing readdata: " + preexistingReadData.size());
            if (!preexistingReadData.isEmpty())
            {
                Map<String, File> existingFileNames = new HashMap<>();
                preexistingReadData.forEach(rd -> {
                    existingFileNames.put(rd.getFile1().getName(), rd.getFile1());
                    if (rd.getFile2() != null)
                    {
                        existingFileNames.put(rd.getFile2().getName(), rd.getFile2());
                    }
                });

                Map<String, File> sharedFns = new HashMap<>();
                for (FileGroup fg : fileGroups)
                {
                    if (r.getFileSetName() != null && r.getFileSetName().equals(fg.name))
                    {
                        getJob().getLogger().debug("Inspecting fileGroup: " + fg.name + ", for readset: " + r.getName() + ", with " + fg.filePairs.size() + " file pairs");
                        for (FileGroup.FilePair fp : fg.filePairs)
                        {
                            if (existingFileNames.containsKey(fp.file1.getName()))
                            {
                                sharedFns.put(fp.file1.getName(), fp.file1);
                            }

                            if (fp.file2 != null && existingFileNames.containsKey(fp.file2.getName()))
                            {
                                sharedFns.put(fp.file2.getName(), fp.file2);
                            }
                        }
                    }
                }

                if (!sharedFns.isEmpty())
                {
                    getJob().getLogger().debug("Duplicate file names found between incoming and existing for: " + r.getName());
                    for (String newFile : sharedFns.keySet())
                    {
                        long diff = Math.abs(sharedFns.get(newFile).length() - existingFileNames.get(newFile).length());
                        getJob().getLogger().debug("File name: " + newFile + ", with size difference: " + diff);
                        if (diff < 100)
                        {
                            throw new PipelineJobException("Identical filenames with nearly identical size detected between existing and new files for readset: " + r.getName());
                        }
                    }
                }
                else
                {
                    getJob().getLogger().debug("No duplicate filenames found");
                }
            }
        }
    }
}

