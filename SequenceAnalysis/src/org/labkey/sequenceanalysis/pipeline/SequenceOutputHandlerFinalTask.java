package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceOutputHandlerFinalTask extends PipelineJob.Task<SequenceOutputHandlerFinalTask.Factory>
{
    private static final String ACTION_NAME = "Processing Files";

    protected SequenceOutputHandlerFinalTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(SequenceOutputHandlerFinalTask.class);
            setLocation("webserver");
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(ACTION_NAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SequenceOutputHandlerFinalTask(this, job);
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private SequenceOutputHandlerJob getPipelineJob()
    {
        return (SequenceOutputHandlerJob)getJob();
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());
        getPipelineJob().setExperimentRunRowId(runId);

        //create analysisRecord
        AnalysisModelImpl am = new AnalysisModelImpl();
        am.setContainer(getJob().getContainerId());
        am.setDescription(getPipelineJob().getDescription());
        am.setRunId(runId);
        //am.setReadset();
        //am.setLibraryId();

        am.setCreated(new Date());
        am.setModified(new Date());
        am.setCreatedby(getJob().getUser().getUserId());
        am.setModifiedby(getJob().getUser().getUserId());
        am.setType(getPipelineJob().getHandler().getName());
        TableInfo analysisTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
        Table.insert(getJob().getUser(), analysisTable, am);
        int analysisId = am.getRowId();

        if (!getPipelineJob().getOutputsToCreate().isEmpty())
        {
            getJob().getLogger().info("creating " + getPipelineJob().getOutputsToCreate().size() + " new output files");

            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
            for (SequenceOutputFile o : getPipelineJob().getOutputsToCreate())
            {
                o.setRunId(runId);
                o.setAnalysis_id(analysisId);
                o.setCreatedby(getJob().getUser().getUserId());
                o.setModifiedby(getJob().getUser().getUserId());
                if (o.getContainer() == null)
                {
                    o.setContainer(getJob().getContainerId());
                }

                if (o.getDataId() == null && o.getFile() != null)
                {
                    getJob().getLogger().debug("creating ExpData for file: " + o.getFile().getName());
                    ExpData d = ExperimentService.get().createData(getJob().getContainer(), new DataType(o.getCategory()));

                    d.setDataFileURI(o.getFile().toURI());
                    d.setName(o.getFile().getName());
                    d.save(getJob().getUser());
                    o.setDataId(d.getRowId());
                }

                Table.insert(getJob().getUser(), ti, o);
            }
        }
        else
        {
            getJob().getLogger().info("no outputs created, nothing to do");
        }

        return new RecordedActionSet();
    }

}
