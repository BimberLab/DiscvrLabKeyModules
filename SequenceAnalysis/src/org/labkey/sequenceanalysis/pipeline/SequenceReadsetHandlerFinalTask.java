package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.FileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceReadsetHandlerFinalTask extends PipelineJob.Task<SequenceReadsetHandlerFinalTask.Factory>
{
    private static final String ACTION_NAME = "Processing Files";

    protected SequenceReadsetHandlerFinalTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(SequenceReadsetHandlerFinalTask.class);
            setLocation("webserver");
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(ACTION_NAME);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SequenceReadsetHandlerFinalTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private SequenceReadsetHandlerJob  getPipelineJob()
    {
        return (SequenceReadsetHandlerJob)getJob();
    }

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());
        getPipelineJob().setExperimentRunRowId(runId);

        List<SequenceOutputFile> outputsCreated = new ArrayList<>();
        if (!getPipelineJob().getOutputsToCreate().isEmpty())
        {
            outputsCreated.addAll(SequenceOutputHandlerFinalTask.createOutputFiles(getPipelineJob(), runId, null));
        }
        else
        {
            getJob().getLogger().info("no outputs created, nothing to do");
        }

        //run final handler
        getPipelineJob().getHandler().getProcessor().complete(getPipelineJob(), getPipelineJob().getReadsets(), outputsCreated);

        return new RecordedActionSet();
    }
}
