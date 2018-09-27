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
import org.apache.commons.lang3.StringUtils;
import org.biojava3.core.sequence.DNASequence;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.ReadDataImpl;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * User: bbimber
 * Date: 4/22/12
 * Time: 4:09 PM
 */
public class IlluminaImportTask extends WorkDirectoryTask<IlluminaImportTask.Factory>
{
    private SequencePipelineSettings _settings;
    private Integer _instrumentRunId = null;
    private static String ACTION_NAME = "Import Illumina Reads";

    protected IlluminaImportTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(IlluminaImportTask.class);
        }

        public String getStatusName()
        {
            return "IMPORTING ILLUMINA READS";
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(ACTION_NAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            IlluminaImportTask task = new IlluminaImportTask(this, job);
            return task;
        }

        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new FileType(".csv"));
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private FileAnalysisJobSupport getSupport()
    {
        return getJob().getJobSupport(FileAnalysisJobSupport.class);
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        _settings = new SequencePipelineSettings(getJob().getParameters());

        List<RecordedAction> actions = new ArrayList<>();

        job.getLogger().info("Starting analysis");
        String prefix = job.getParameters().get("fastqPrefix");

        List<File> inputFiles = getSupport().getInputFiles();
        if (inputFiles.size() == 0)
            throw new PipelineJobException("No input files");

        DbSchema schema = SequenceAnalysisSchema.getInstance().getSchema();

        handleInstrumentRun(schema);

        //iterate over each CSV
        for (File input : inputFiles)
        {
            RecordedAction action = new RecordedAction(ACTION_NAME);
            action.addInputIfNotPresent(input, "Illumina Sample CSV");

            Map<String, Integer> sampleMap = parseCsv(input, schema);

            //NOTE: it might be just as easy to match filename based on expected pattern

            //this step will be slow
            IlluminaFastqSplitter<Integer> parser = new IlluminaFastqSplitter<>("Illumina", sampleMap, job.getLogger(), input.getParent(), prefix);
            parser.setOutputGzip(true);
            parser.setDestinationDir(getSupport().getAnalysisDirectory());

            // the first element of the pair is the sample ID.  the second is either 1 or 2,
            // depending on whether the file represents the forward or reverse reads
            Map<Pair<Integer, Integer>, File> fileMap = parser.parseFastqFiles();

            for (File f : parser.getFiles())
            {
                action.addInputIfNotPresent(f, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME);
            }

            getJob().getLogger().info("Created " + fileMap.keySet().size() + " FASTQ files");

            getJob().getLogger().info("Compressing FASTQ files");
            FileType gz = new FileType("gz");
            for (Pair<Integer, Integer> sampleKey : fileMap.keySet())
            {
                File inputFile = fileMap.get(sampleKey);
                if (!gz.isType(inputFile))
                {
                    job.getLogger().info("compressing: " + inputFile.getName());
                    File output = Compress.compressGzip(inputFile);
                    inputFile.delete();
                    if (inputFile.exists())
                        throw new PipelineJobException("Unable to delete file: " + inputFile.getPath());

                    fileMap.put(sampleKey, output);
                }
            }

            TableInfo rs = schema.getTable(SequenceAnalysisSchema.TABLE_READSETS);
            TableInfo readDataTable = schema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA);

            Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob(), false);

            //update the readsets
            Map<String, Object> row;
            for (Object key : new HashSet<>(sampleMap.values()))
            {
                Integer readsetId = (Integer) key;

                Readset readset = SequenceAnalysisService.get().getReadset(readsetId, getJob().getUser());

                row = new HashMap<>();
                Pair<Integer, Integer> pair = Pair.of(readsetId, 1);
                ReadDataImpl rd = new ReadDataImpl();
                rd.setReadset(readsetId);
                rd.setCreated(new Date());
                rd.setCreatedBy(getJob().getUser().getUserId());
                rd.setModified(new Date());
                rd.setModifiedBy(getJob().getUser().getUserId());
                rd.setRunId(runId);
                rd.setPlatformUnit(readsetId.toString());  //this isnt quite correct, but we dont have much else to work with

                if (fileMap.containsKey(pair))
                {
                    action.addOutput(fileMap.get(pair), "FASTQ File", false, true);
                    ExpData d = createExpData(fileMap.get(pair));
                    if (d != null)
                    {
                        rd.setFileId1(d.getRowId());

                        //now add quality metrics
                        addQualityMetrics(schema, readsetId, pair, parser, d);
                    }
                    else
                    {
                        getJob().getLogger().error("Unable to create ExpData for: " + fileMap.get(pair).getPath());
                        continue;
                    }
                }
                else
                {
                    getJob().getLogger().warn("No output file was created for readset: " + readsetId + ", skipping");
                    continue;
                }

                pair = Pair.of(readsetId, 2);
                if (fileMap.containsKey(pair))
                {
                    action.addOutput(fileMap.get(pair), "Paired FASTQ File", false, true);
                    ExpData d = createExpData(fileMap.get(pair));
                    if (d != null)
                    {
                        rd.setFileId2(d.getRowId());

                        //now add quality metrics
                        addQualityMetrics(schema, readsetId, pair, parser, d);
                    }
                    else
                        getJob().getLogger().error("Unable to create ExpData for: " + fileMap.get(pair).getPath());
                }

                if (readsetId == 0)
                    continue;

                rd.setContainer(readset.getContainer());
                row.put("rowid", readsetId);

                if (_instrumentRunId != null)
                    row.put("instrument_run_id", _instrumentRunId);

                if (runId != null)
                    row.put("runid", runId);

                try
                {
                    Table.update(getJob().getUser(), rs, row, readsetId);
                    getJob().getLogger().info("Updated readset: " + readsetId);

                    getJob().getLogger().debug("creating readdata");
                    Table.insert(getJob().getUser(), readDataTable, rd);
                }
                catch (OptimisticConflictException e)
                {
                    //row doesnt exist..
                    getJob().getLogger().error("readset doesn't exist: " + readsetId);
                }

                actions.add(action);
            }
        }

        return new RecordedActionSet(actions);
    }

    private void handleInstrumentRun(DbSchema schema)
    {
        TableInfo runTable = schema.getTable(SequenceAnalysisSchema.TABLE_INSTRUMENT_RUNS);

        //otherwise create the machine run record:
        Map<String, Object> runRow = new HashMap<>();
        runRow.put("name", _settings.getRunName());
        runRow.put("rundate", _settings.getRunDate());
        runRow.put("instrumentid", _settings.getInstrumentId());

        runRow.put("container", getJob().getContainer().getId());
        runRow.put("createdby", getJob().getUser().getUserId());
        runRow.put("created", new Date());

        runRow = Table.insert(getJob().getUser(), runTable, runRow);

        _instrumentRunId = (Integer)runRow.get("rowid");
        getJob().getLogger().info("Created run: " + _instrumentRunId);
    }

    private void addQualityMetrics(DbSchema schema, int readsetId, Pair<Integer, Integer> key, IlluminaFastqSplitter parser, ExpData d)
    {
        getJob().getLogger().info("Adding quality metrics for file: " + d.getFile().getName());
        Map<Pair<Integer, Integer>, Integer> readCounts = parser.getReadCounts();

        //NOTE: still insert a record if the count is zero, since this might be interesting to know
        Integer count = readCounts.get(key);

        Map<String, Object> r = new HashMap<>();
        r.put("metricname", "Total Reads");
        r.put("metricvalue", count);
        r.put("dataid", d.getRowId());
        if (readsetId > 0)
            r.put("readset", readsetId);

        r.put("container", getJob().getContainer());
        r.put("createdby", getJob().getUser().getUserId());

        if (_instrumentRunId > 0)
            r.put("runid", _instrumentRunId);

        Table.insert(getJob().getUser(), schema.getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS), r);
    }

    private ExpData createExpData(File f)
    {
        return SequenceTaskHelper.createExpData(f, getJob());
    }

    private Map<String, Integer> parseCsv(File sampleFile, DbSchema schema) throws PipelineJobException
    {
        getJob().getLogger().info("Parsing Sample File: " + sampleFile.getName());
        try (CSVReader reader = new CSVReader(Readers.getReader(sampleFile)))
        {
            //parse the samples file
            String [] nextLine;
            Map<String, Integer> sampleMap = new HashMap<>();
            sampleMap.put("S0", 0); //placeholder for control and unmapped reads

            Boolean inSamples = false;
            int sampleIdx = 0;
            while ((nextLine = reader.readNext()) != null)
            {
                getJob().getLogger().debug("Parsing line starting with: " + nextLine[0]);
                if (nextLine.length > 0 && "[Data]".equals(nextLine[0]))
                {
                    inSamples = true;
                    continue;
                }

                //NOTE: for now we only parse samples.  at a future point we might consider using more of this file
                if (!inSamples)
                    continue;

                if (nextLine.length == 0 || null == nextLine[0])
                    continue;

                if ("Sample_ID".equalsIgnoreCase(nextLine[0]))
                    continue;

                try
                {
                    //parse out barcodes.  note: the first barcode is reversed in the read header, at least for MiSeq
                    //String indexes = nextLine[6] + "+" + nextLine[8];
                    String indexesRC = new DNASequence(nextLine[6]).getReverseComplement().getSequenceAsString() + "+" + nextLine[8];

                    sampleIdx++;
                    Integer readsetId;
                    try
                    {
                        readsetId = Integer.parseInt(nextLine[0]);
                    }
                    catch (NumberFormatException e)
                    {
                        getJob().getLogger().warn("The sample Id was not an integer: " + nextLine[0]);
                        readsetId = tryCreateReadset(schema, nextLine);
                    }

                    if (!validateReadsetId(readsetId))
                    {
                        readsetId = tryCreateReadset(schema, nextLine);
                    }

                    //sampleMap.put(indexes, readsetId);
                    sampleMap.put(indexesRC, readsetId);
                    sampleMap.put("S" + sampleIdx, readsetId);
                }
                catch (PipelineValidationException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            getJob().getLogger().info("Found " + sampleMap.size() + " samples");

            return sampleMap;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    //not very efficient, but we only expect a handful of samples per run
    private boolean validateReadsetId(Integer id) throws PipelineJobException, PipelineValidationException
    {
        getJob().getLogger().debug("attempting to resolve readset by Id: " + id);
        if (id == null)
        {
            return false;
        }

        TableSelector ts = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_READSETS), new SimpleFilter(FieldKey.fromString("rowid"), id), null);
        if (!ts.exists())
        {
            getJob().getLogger().warn("No readsets found that match Id " + id + ", skipping.");
            return false;
        }

        TableSelector ts2 = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_READ_DATA), new SimpleFilter(FieldKey.fromString("readset"), id), null);
        if (ts2.exists())
        {
            getJob().getLogger().warn("Readset " + id + " already has a file associated with it and cannot be re-imported.  It will be skipped");
            return false;
        }

        return true;
    }

    private Integer tryCreateReadset(DbSchema schema, String[] line) throws PipelineJobException
    {
        if (!_settings.doAutoCreateReadsets())
            throw new PipelineJobException("Unable to find existing readset matching ID: " + line[0]);

        String name = line[0];
        getJob().getLogger().info("Creating readset with name: " + name);

        String barcode5 = null;
        String barcode3 = null;
        if (line.length >= 5)
            barcode5 = resolveBarcode(line[4]);
        if (line.length >= 7)
            barcode3 = resolveBarcode(line[6]);

        TableInfo rsTable = schema.getTable(SequenceAnalysisSchema.TABLE_READSETS);
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("platform", "ILLUMINA");

        if (barcode5 != null)
            row.put("barcode5", barcode5);
        if (barcode3 != null)
            row.put("barcode3", barcode3);

        if (_instrumentRunId != null)
            row.put("instrument_run_id", _instrumentRunId);

        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob(), false);
        if (runId != null)
            row.put("runid", runId);

        row.put("container", getJob().getContainer().getId());
        row.put("createdby", getJob().getUser().getUserId());
        row.put("created", new Date());

        row = Table.insert(getJob().getUser(), rsTable, row);
        return (Integer)row.get("rowid");
    }

    private String resolveBarcode(String barcode)
    {
        if (StringUtils.isEmpty(barcode))
            return null;

        //attempt to resolve against table
        return new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_BARCODES), PageFlowUtil.set("tag_name"), new SimpleFilter(FieldKey.fromString("sequence"), barcode), null).getObject(String.class);
    }
}
