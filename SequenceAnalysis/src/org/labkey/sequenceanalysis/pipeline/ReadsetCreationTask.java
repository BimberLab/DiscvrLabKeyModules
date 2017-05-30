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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.ReadDataImpl;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.run.util.FastqcRunner;
import org.labkey.sequenceanalysis.util.FastqUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bbimber
 * Date: 5/2/12
 * Time: 7:11 PM
 */
public class ReadsetCreationTask extends PipelineJob.Task<ReadsetCreationTask.Factory>
{
    private SequencePipelineSettings _settings;
    private static final String ACTIONNAME = "Creating Readset";

    protected ReadsetCreationTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(ReadsetCreationTask.class);

            setJoin(true);  // Do this once per file-set.
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new ReadsetCreationTask(this, job);
        }

        public List<FileType> getInputTypes()
        {
            return null;
        }

        public String getStatusName()
        {
            return ACTIONNAME;
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(ACTIONNAME);
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        _settings = new SequencePipelineSettings(getJob().getParameters());

        try
        {
            getJob().setStatus(PipelineJob.TaskStatus.running, "IMPORTING READS");
            List<RecordedAction> actions = new ArrayList<>();

            //NOTE: this runs after XARgenerator, so it cannot create or modify any additional files
            importReadsets();

            getJob().setStatus(PipelineJob.TaskStatus.complete);
            return new RecordedActionSet(actions);
        }
        catch (Exception e)
        {
            getJob().setStatus(PipelineJob.TaskStatus.error);
            throw new PipelineJobException(e);
        }
    }

    private ReadsetImportJob getPipelineJob()
    {
        return (ReadsetImportJob)getJob();
    }

    private void importReadsets() throws PipelineJobException
    {
        SequencePipelineSettings settings = getSettings();
        DbSchema schema = SequenceAnalysisSchema.getInstance().getSchema();

        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());
        ExpRun run = ExperimentService.get().getExpRun(runId);
        List<ExpData> datas = new ArrayList<>();
        datas.addAll(run.getInputDatas(SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, ExpProtocol.ApplicationType.ExperimentRunOutput));
        datas.addAll(run.getInputDatas(SequenceTaskHelper.BARCODED_FASTQ_OUTPUTNAME, ExpProtocol.ApplicationType.ExperimentRunOutput));

        getJob().getLogger().debug("Total normalized sequence files created: " + datas.size());

        List<SequenceReadsetImpl> newReadsets = new ArrayList<>();

        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
        {
            TableInfo readsetTable = schema.getTable(SequenceAnalysisSchema.TABLE_READSETS);
            TableInfo readDataTable = schema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA);

            for (Readset rs : getPipelineJob().getSequenceSupport().getCachedReadsets())
            {
                SequenceReadsetImpl r = (SequenceReadsetImpl)rs;
                boolean updateExisting = r.getReadsetId() != null && r.getReadsetId() > 0;

                getJob().getLogger().info("Starting readset " + r.getName());

                SequenceReadsetImpl row;
                if (!updateExisting)
                {
                    row = new SequenceReadsetImpl();

                    row.setSampleId(r.getSampleId());
                    row.setSubjectId(r.getSubjectId());
                    row.setSampleDate(r.getSampleDate());
                    row.setPlatform(r.getPlatform());
                    row.setSampleType(r.getSampleType());
                    row.setLibraryType(r.getLibraryType());
                    row.setName(r.getName());
                    row.setComments(r.getComments());
                    row.setApplication(r.getApplication());
                    row.setChemistry(r.getChemistry());
                    row.setInstrumentRunId(r.getInstrumentRunId());
                    row.setBarcode3(r.getBarcode3());
                    row.setBarcode5(r.getBarcode5());

                    row.setContainer(getJob().getContainer().getId());
                    row.setCreatedBy(getJob().getUser().getUserId());
                    row.setCreated(new Date());
                }
                else
                {
                    row = SequenceAnalysisServiceImpl.get().getReadset(r.getReadsetId(), getJob().getUser());
                    if (row == null)
                    {
                        throw new PipelineJobException("Unable to find existing readset with id: " + r.getReadsetId());
                    }

                    if (row.getReadsetId() == null)
                    {
                        throw new PipelineJobException("Readset lacks a rowid: " + r.getReadsetId());
                    }
                }

                //now add readData
                List<ReadDataImpl> readDatas = new ArrayList<>();
                for (ReadDataImpl rd : r.getReadDataImpl())
                {
                    File f1 = rd.getFile1();
                    File f2 = rd.getFile2();

                    getJob().getLogger().debug("Total exp data files: " + datas.size());
                    if (datas.size() > 0)
                    {
                        boolean found = false;
                        boolean found2 = false;
                        for (ExpData d : datas)
                        {
                            getJob().getLogger().debug("Inspecting exp data file: " + d.getFile().getPath());
                            if (f1.getName().equals(d.getFile().getName()))
                            {
                                if (found)
                                {
                                    getJob().getLogger().warn("ERROR: More than 1 matching file found for: " + f1.getName());
                                    getJob().getLogger().warn("File was: " + d.getFile().getPath());
                                }

                                if (!d.getFile().exists())
                                    throw new PipelineJobException("Expected file does not exist: " + d.getFile().getPath());

                                rd.setFileId1(d.getRowId());
                                found = true;
                            }

                            if (f2 != null && f2.getName().equals(d.getFile().getName()))
                            {
                                if (found2)
                                {
                                    getJob().getLogger().warn("ERROR: More than 1 matching file found for: " + f2.getName());
                                    getJob().getLogger().warn("File was: " + d.getFile().getPath());
                                }

                                if (!d.getFile().exists())
                                    throw new PipelineJobException("Expected file does not exist: " + d.getFile().getPath());

                                rd.setFileId2(d.getRowId());
                                found2 = true;
                            }
                        }
                    }

                    if (rd.getFileId1() == null)
                    {
                        // the rationale here is that an output file should always exist for each readset, unless the input was barcoded,
                        // in which case its possible to lack reads without the user knowing upfront
                        if (!getSettings().isDoBarcode())
                        {
                            throw new PipelineJobException("Unable to identify FASTQ for reads, expected: " + (rd.getFile1() == null ? "" : rd.getFile1().getName()));
                        }
                        else
                        {
                            getJob().getLogger().warn("No output file was found for reads: " + r.getName() + ", it will not be imported");
                            continue;
                        }
                    }

                    rd.setRunId(runId);

                    readDatas.add(rd);
                }

                row.setRunId(runId);
                row.setModified(new Date());
                row.setModifiedBy(getJob().getUser().getUserId());

                //then import
                if (readDatas.isEmpty())
                {
                    getJob().getLogger().info("no reads found for readset: " + r.getName() + ", skipping import");
                    continue;
                }
                row.setReadData(readDatas);

                SequenceReadsetImpl newRow;
                if (!updateExisting)
                {
                    newRow = Table.insert(getJob().getUser(), readsetTable, row);
                    getJob().getLogger().info("Created readset: " + newRow.getReadsetId());
                    newReadsets.add(newRow);
                }
                else
                {
                    getJob().getLogger().info("Updating existing readset: " + row.getReadsetId());
                    newRow = Table.update(getJob().getUser(), readsetTable, row, row.getReadsetId());
                    newReadsets.add(newRow);
                    if (newRow.getReadsetId() == null || newRow.getReadsetId() == 0)
                    {
                        getJob().getLogger().warn("no readsetId found after updating readset: " + row.getName());
                        getJob().getLogger().warn("using rowId from original model: " + r.getReadsetId());
                        newRow.setRowId(row.getReadsetId());
                    }
                }

                if (newRow.getReadsetId() == null || newRow.getReadsetId() == 0)
                {
                    throw new PipelineJobException("Readset Id not found");
                }

                //create ReadData
                getJob().getLogger().debug(readDatas.size() + " file pairs to insert");
                for (ReadDataImpl rd : readDatas)
                {
                    getJob().getLogger().debug("creating read data for readset: " + newRow.getReadsetId());
                    if (newRow.getReadsetId() == null)
                    {
                        throw new PipelineJobException("no readsetId found for: " + newRow.getName());
                    }
                    rd.setReadset(newRow.getReadsetId());
                    rd.setContainer(getJob().getContainer().getId());
                    rd.setCreatedBy(getJob().getUser().getUserId());
                    rd.setCreated(new Date());
                    rd.setModifiedBy(getJob().getUser().getUserId());
                    rd.setModified(new Date());

                    Table.insert(getJob().getUser(), readDataTable, rd);
                }
            }

            transaction.commit();
        }

        //NOTE: this is outside the transaction because it can take a long time.
        int idx = 0;
        for (SequenceReadsetImpl model : newReadsets)
        {
            idx++;
            getJob().getLogger().info("calculating quality metrics for readset: " + model.getName() + ", " + idx + " of " + newReadsets.size());
            for (ReadDataImpl d : model.getReadDataImpl())
            {
                getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING QUALITY METRICS (" + idx + " of " + newReadsets.size() + ")");
                addQualityMetricsForReadset(model, d.getFileId1(), getJob());
                if (d.getFileId2() != null)
                {
                    addQualityMetricsForReadset(model, d.getFileId2(), getJob());
                }

                if (settings.isRunFastqc())
                {
                    getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING FASTQC");
                    runFastqcForFile(d.getFileId1());
                    if (d.getFileId2() != null)
                    {
                        runFastqcForFile(d.getFileId2());
                    }
                }
            }
        }
    }

    private void runFastqcForFile(Integer fileId) throws PipelineJobException
    {
        ExpData d1 = ExperimentService.get().getExpData(fileId);
        if (d1 != null && d1.getFile().exists())
        {
            try
            {
                getJob().getLogger().info("running FastQC for file: " + d1.getFile().getName());

                //NOTE: task is on webserver, so use single thread only
                FastqcRunner runner = new FastqcRunner(getJob().getLogger());
                runner.execute(Arrays.asList(d1.getFile()), null);
                getJob().getLogger().info("done");
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    public static void addQualityMetricsForReadset(Readset rs, int fileId, PipelineJob job) throws PipelineJobException
    {
        try
        {
            ExpData d = ExperimentService.get().getExpData(fileId);
            File cachedMetrics = new File(d.getFile().getPath() + ".metrics");
            Map<String, Object> metricsMap;
            if (cachedMetrics.exists())
            {
                job.getLogger().debug("reading previously calculated metrics from file: " + cachedMetrics.getPath());
                metricsMap = new HashMap<>();
                try (CSVReader reader = new CSVReader(Readers.getReader(cachedMetrics), '\t'))
                {
                    String[] line;
                    while ((line = reader.readNext()) != null)
                    {
                        metricsMap.put(line[0], line[1]);
                    }
                }
            }
            else
            {
                metricsMap = FastqUtils.getQualityMetrics(d.getFile(), job.getLogger());
            }

            for (String metricName : metricsMap.keySet())
            {
                Map<String, Object> r = new HashMap<>();
                r.put("metricname", metricName);
                r.put("metricvalue", metricsMap.get(metricName));
                r.put("dataid", d.getRowId());
                r.put("readset", rs.getReadsetId());
                r.put("container", rs.getContainer() == null ? job.getContainer() : rs.getContainer());
                r.put("createdby", job.getUser().getUserId());

                Table.insert(job.getUser(), SequenceAnalysisManager.get().getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS), r);
            }

            if (cachedMetrics.exists())
            {
                cachedMetrics.delete();
            }
        }
        catch (Exception e)
        {
            //TODO: roll back changes?

            throw new PipelineJobException(e);
        }
    }

    private SequencePipelineSettings getSettings()
    {
        return _settings;
    }
}
