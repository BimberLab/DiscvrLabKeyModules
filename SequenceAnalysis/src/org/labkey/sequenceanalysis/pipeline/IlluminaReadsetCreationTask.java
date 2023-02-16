package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.pipeline.AbstractSequenceTaskFactory;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 11/16/12
 * Time: 6:35 PM
 */
public class IlluminaReadsetCreationTask extends WorkDirectoryTask<IlluminaReadsetCreationTask.Factory>
{
    private static final String ACTION_NAME = "Import Illumina Reads";

    protected IlluminaReadsetCreationTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(IlluminaReadsetCreationTask.class);
        }

        @Override
        public String getStatusName()
        {
            return "IMPORTING ILLUMINA READS";
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(ACTION_NAME);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            IlluminaReadsetCreationTask task = new IlluminaReadsetCreationTask(this, job);
            return task;
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new FileType(".csv"));
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();

        job.getLogger().info("Updating readsets");
        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());

        DbSchema schema = SequenceAnalysisSchema.getInstance().getSchema();

        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
        {
            TableInfo rs = schema.getTable(SequenceAnalysisSchema.TABLE_READSETS);
            TableInfo readData = schema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA);

            ExpRun run = ExperimentService.get().getExpRun(runId);
            List<Integer> dataIds = new ArrayList<>();
            List<ExpData> outputs = run.getDataOutputs();
            for (ExpData d : outputs)
            {
                dataIds.add(d.getRowId());
            }

            TableSelector ts = new TableSelector(readData, Collections.singleton("readset"), new SimpleFilter(FieldKey.fromString("fileid1"), dataIds, CompareType.IN), null);
            Integer[] readsets = ts.getArray(Integer.class);
            Map<String, Object> row = new HashMap<>();
            row.put("runId", runId);

            for (Integer rowId : readsets)
            {
                Table.update(getJob().getUser(), rs, row, rowId);
            }

            transaction.commit();
        }
        catch (RuntimeSQLException e)
        {
            throw new PipelineJobException(e);
        }

        return new RecordedActionSet();
    }
}