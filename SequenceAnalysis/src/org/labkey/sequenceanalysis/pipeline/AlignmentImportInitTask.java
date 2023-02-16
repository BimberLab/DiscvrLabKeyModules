package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.pipeline.AbstractSequenceTaskFactory;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/4/2014.
 */
public class AlignmentImportInitTask extends WorkDirectoryTask<AlignmentImportInitTask.Factory>
{
    protected AlignmentImportInitTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(AlignmentImportInitTask.class);
        }

        @Override
        public String getStatusName()
        {
            return "IMPORTING ALIGNMENT";
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return List.of();
        }

        @Override
        public PipelineJob.Task<?> createTask(PipelineJob job)
        {
            AlignmentImportInitTask task = new AlignmentImportInitTask(this, job);
            return task;
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new FileType(".bam"));
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private AlignmentImportJob getPipelineJob()
    {
        return (AlignmentImportJob)getJob();
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        //create analysis records
        parseAndCreateReadsets();

        return new RecordedActionSet();
    }

    private List<AnalysisModel> parseAndCreateReadsets() throws PipelineJobException
    {
        DbSchema schema = SequenceAnalysisSchema.getInstance().getSchema();
        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
        {
            SequencePipelineSettings settings = new SequencePipelineSettings(getJob().getParameters());

            //ensure readsets exist
            TableInfo rs = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READSETS);

            List<AnalysisModel> ret = new ArrayList<>();
            Map<String, String> params = getJob().getParameters();
            for (String key : params.keySet())
            {
                if (key.startsWith("readset_"))
                {
                    JSONObject o = new JSONObject(params.get(key));

                    SequenceReadsetImpl r = settings.createReadsetModel(o);
                    if (r.getReadsetId() == null || r.getReadsetId() == 0)
                    {
                        r.setContainer(getJob().getContainer().getId());
                        r.setCreated(new Date());
                        r.setCreatedBy(getJob().getUser().getUserId());
                        r.setModified(new Date());
                        r.setModifiedBy(getJob().getUser().getUserId());
                        //TODO
                        //r.setRunId(runId);

                        r = Table.insert(getJob().getUser(), rs, r);
                        getJob().getLogger().info("Created readset: " + r.getReadsetId());
                    }
                    else
                    {
                        //verify this readset exists:
                        r = new TableSelector(rs, new SimpleFilter(FieldKey.fromString("rowid"), r.getReadsetId()), null).getObject(SequenceReadsetImpl.class);
                        if (r == null)
                        {
                            throw new PipelineJobException("Readset with RowId: " + r.getReadsetId() + " does not exist, aborting");
                        }
                    }

                    getPipelineJob().getSequenceSupport().cacheGenome(SequenceAnalysisService.get().getReferenceGenome(o.getInt("library_id"), getJob().getUser()));
                    getPipelineJob().getSequenceSupport().cacheReadset(r);
                }
            }

            transaction.commit();

            return ret;
        }
    }
}
