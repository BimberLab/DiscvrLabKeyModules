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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.util.FastqUtils;

import java.sql.SQLException;
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

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        _settings = new SequencePipelineSettings(getJob().getParameters());

        try
        {
            getJob().setStatus("Importing");
            List<RecordedAction> actions = new ArrayList<>();

            //NOTE: this runs after XARgenerator, so it cannot create or modify any additional files
            importReadsets();

            getJob().setStatus("Complete");
            return new RecordedActionSet(actions);
        }
        catch (Exception e)
        {
            getJob().setStatus("ERROR");
            throw new PipelineJobException(e);
        }
    }

    private void importReadsets() throws PipelineJobException
    {
        SequencePipelineSettings settings = getSettings();
        DbSchema schema = SequenceAnalysisSchema.getInstance().getSchema();

        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());
        ExpRun run = ExperimentService.get().getExpRun(runId);
        List<? extends ExpData> datas = run.getInputDatas(SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, ExpProtocol.ApplicationType.ExperimentRunOutput);
        getJob().getLogger().debug("Total normalized sequence files created: " + datas.size());

        ReadsetModel row;
        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
        {
            TableInfo rs = schema.getTable(SequenceAnalysisSchema.TABLE_READSETS);

            List<ReadsetModel> readsets = settings.getReadsets();
            List<ReadsetModel> newReadsets = new ArrayList<>();

            for(ReadsetModel r : readsets)
            {
                getJob().getLogger().info("Starting readset " + r.getName());
                row = new ReadsetModel();

                row.setSampleId(r.getSampleId());
                row.setSubjectId(r.getSubjectId());
                row.setSampleDate(r.getSampleDate());
                row.setPlatform(r.getPlatform());
                row.setName(r.getName());
                row.setInstrumentRunId(r.getInstrumentRunId());

                row.setContainer(getJob().getContainer().getId());
                row.setCreatedBy(getJob().getUser().getUserId());
                row.setCreated(new Date());

                String fn = r.getFileName();
                String fn2 = r.getFileName2();

                String expectedName = StringUtils.isEmpty(fn) ? null : r.getExpectedFileNameForPrefix(fn, true);
                String expectedName2 = StringUtils.isEmpty(fn2) ? null : r.getExpectedFileNameForPrefix(fn2, true);

                getJob().getLogger().debug("Total exp data files: " + datas.size());
                if (datas.size() > 0)
                {
                    boolean found = false;
                    for (ExpData d : datas)
                    {
                        getJob().getLogger().debug("Inspecting exp data file: " + d.getFile().getPath());
                        if (expectedName.equals(d.getFile().getName()))
                        {
                            if (found)
                            {
                                getJob().getLogger().warn("ERROR: More than 1 matching file found for: " + expectedName);
                                getJob().getLogger().warn("File was: " + d.getFile().getPath());
                            }

                            if (!d.getFile().exists())
                                throw new PipelineJobException("Expected file does not exist: " + d.getFile().getPath());

                            row.setFileId(d.getRowId());
                            found = true;
                        }

                        if (expectedName2 != null && expectedName2.equals(d.getFile().getName()))
                        {
                            if (found)
                            {
                                getJob().getLogger().warn("ERROR: More than 1 matching file found second mate file: " + expectedName2);
                                getJob().getLogger().warn("File was: " + d.getFile().getPath());
                            }

                            if (!d.getFile().exists())
                                throw new PipelineJobException("Expected file does not exist: " + d.getFile().getPath());

                            row.setFileId2(d.getRowId());
                            found = true;
                        }
                    }
                }


                if(row.getFileId() == null)
                {
                    // the rationale here is that an output file should always exist for each readset, unless the input was barcoded,
                    // in which case its possible to lack reads without the user knowing upfront
                    if (!getSettings().isDoBarcode())
                    {
                        throw new PipelineJobException("Unable to identify FASTQ for readset, expected: " + expectedName);
                    }
                    else
                    {
                        getJob().getLogger().warn("No output file was found for readset: " + r.getName() + ", it will not be imported");
                        continue;
                    }
                }
                row.setRunId(SequenceTaskHelper.getExpRunIdForJob(getJob()));

                //then import
                if(r.getReadsetId() == null || r.getReadsetId() == 0)
                {
                    ReadsetModel newRow = Table.insert(getJob().getUser(), rs, row);
                    getJob().getLogger().info("Created readset: " + newRow.getRowId());
                    newReadsets.add(newRow);
                }
                else
                {
                    Integer[] pks = {r.getReadsetId()};
                    ReadsetModel newRow = Table.update(getJob().getUser(), rs, row, pks);
                    newReadsets.add(newRow);
                    getJob().getLogger().info("Updated readset: " + r.getReadsetId());
                }
            }

            for (ReadsetModel model : newReadsets)
            {
                addQualityMetricsForReadset(model, model.getFileId());
                if (model.getFileId2() != null)
                {
                    addQualityMetricsForReadset(model, model.getFileId2());
                }
            }

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void addQualityMetricsForReadset(ReadsetModel rs, int fileId) throws SQLException
    {
        ExpData d = ExperimentService.get().getExpData(fileId);
        Map<String, Object> metricsMap = FastqUtils.getQualityMetrics(d.getFile());
        for (String metricName : metricsMap.keySet())
        {
            Map<String, Object> r = new HashMap<>();
            r.put("metricname", metricName);
            r.put("metricvalue", metricsMap.get(metricName));
            r.put("dataid", d.getRowId());
            r.put("readset", rs.getReadsetId());
            r.put("container", getJob().getContainer());
            r.put("createdby", getJob().getUser().getUserId());

            Table.insert(getJob().getUser(), SequenceAnalysisManager.get().getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS), r);
        }
    }

    private SequencePipelineSettings getSettings()
    {
        return _settings;
    }
}
