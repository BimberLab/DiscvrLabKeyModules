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
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.FileGroup;
import org.labkey.sequenceanalysis.ReadDataImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.NucleotideSequenceFileType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
public class ReadsetImportTask extends WorkDirectoryTask<ReadsetImportTask.Factory>
{
    private SequenceTaskHelper _taskHelper;
    private FileAnalysisJobSupport _support;

    private static final String ACTION_NAME = "IMPORTING READSET";
    private static String COMPRESS_INPUT_ACTIONNAME = "Compressing FASTQ Files";

    protected ReadsetImportTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(ReadsetImportTask.class);

            setJoin(true);  // Do this once per file-set.
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new ReadsetImportTask(this, job);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new NucleotideSequenceFileType());
        }

        public String getStatusName()
        {
            return "IMPORT READSET";
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(ACTION_NAME, COMPRESS_INPUT_ACTIONNAME);
        }

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

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        _support = (FileAnalysisJobSupport) job;
        _taskHelper = new SequenceTaskHelper(job, _wd);

        getJob().getLogger().info("Importing sequence data");
        getJob().getLogger().info("input files:");
        for (File input : getHelper().getSupport().getInputFiles())
        {
            getJob().getLogger().info(input.getPath());
        }

        _taskHelper.createExpDatasForInputs();

        List<RecordedAction> actions = new ArrayList<>();
        RecordedAction action = new RecordedAction(COMPRESS_INPUT_ACTIONNAME);
        actions.add(action);

        //NOTE: if none of the files require external normalization, we handle all processing now and skip the external step
        //however, since processing usually deletes/moves the input, we only process files now if this step will be skipped

        try
        {
            List<FileGroup> fileGroups = getHelper().getSettings().getFileGroups(getJob().getJobSupport(SequenceAnalysisJob.class));
            List<SequenceReadsetImpl> readsets = getHelper().getSettings().getReadsets(getJob().getJobSupport(SequenceAnalysisJob.class));

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

                getHelper().getFileManager().handleInputs();
            }
            else
            {
                getJob().getLogger().info("At least one file requires external normalization, so handling of inputs will be deferred");
            }

            if (getHelper().getSettings().isDoBarcode())
            {
                writeExtraBarcodes();
            }

            SequenceAnalysisJob pipelineJob = getJob().getJobSupport(SequenceAnalysisJob.class);
            for (SequenceReadsetImpl rs : readsets)
            {
                getJob().getLogger().debug("caching readset: " + rs.getName() + " with " + rs.getReadData().size() + " files");
                pipelineJob.cacheReadset(rs);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        getHelper().getFileManager().cleanup();

        return new RecordedActionSet(actions);
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

        getHelper().getFileManager().addInput(action, getHelper().SEQUENCE_DATA_INPUT_NAME, f);

        File output;
        if (!gz.isType(f))
        {
            getJob().getLogger().info("\tcompressing input file: " + f.getName());
            output = new File(getHelper().getSupport().getAnalysisDirectory(), f.getName() + ".gz");
            //note: the non-compressed file will potentially be deleted during cleanup, depending on the selection for input handling
            Compress.compressGzip(f, output);
        }
        else
        {
            output = new File(getHelper().getSupport().getAnalysisDirectory(), f.getName());
            if (!output.exists())
            {
                if (getHelper().getFileManager().getInputfileTreatment().equals("delete") || getHelper().getFileManager().getInputfileTreatment().equals("compress"))
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

        getHelper().getFileManager().addOutput(action, getHelper().NORMALIZED_FASTQ_OUTPUTNAME, output);
        getHelper().getFileManager().addUnalteredOutput(output); //even if compressed, it was not really changed and we shouldnt copy it back to the server
        getHelper().getFileManager().addFinalOutputFile(output);

        return output;
    }

    public void writeExtraBarcodes() throws PipelineJobException
    {
        BarcodeModel[] models = getAdditionalBarcodes();
        if (models != null && models.length > 0)
        {
            File extraBarcodes = getExtraBarcodesFile(getHelper());
            getJob().getLogger().debug("\tWriting additional barcodes to file: " + extraBarcodes.getPath());
            CSVWriter writer = null;
            try
            {
                writer = new CSVWriter(new FileWriter(extraBarcodes), '\t', CSVWriter.NO_QUOTE_CHARACTER);
                for (BarcodeModel m : models)
                {
                    writer.writeNext(new String[]{m.getName(), m.getSequence()});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            finally
            {
                if (writer != null)
                {
                    try
                    {
                        writer.close();
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
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
        return new File(helper.getSupport().getAnalysisDirectory(), "extraBarcodes.txt");
    }
}

