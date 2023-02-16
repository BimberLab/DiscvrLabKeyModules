package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.ParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceReadsetHandlerInitTask extends PipelineJob.Task<SequenceReadsetHandlerInitTask.Factory>
{
    protected SequenceReadsetHandlerInitTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(SequenceReadsetHandlerInitTask.class);
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
            List<String> allowableNames = new ArrayList<>();
            for (SequenceOutputHandler handler : SequenceAnalysisServiceImpl.get().getFileHandlers(SequenceOutputHandler.TYPE.Readset))
            {
                allowableNames.add(handler.getName());
            }

            return allowableNames;
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SequenceReadsetHandlerInitTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private SequenceReadsetHandlerJob getPipelineJob()
    {
        return (SequenceReadsetHandlerJob)getJob();
    }

    @NotNull @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        List<RecordedAction> actions = new ArrayList<>();

        SequenceOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor> handler = getPipelineJob().getHandler();
        List<SequenceOutputFile> outputsToCreate = new ArrayList<>();

        for (Readset f : getPipelineJob().getReadsets())
        {
            if (!(f instanceof SequenceReadsetImpl))
            {
                throw new PipelineJobException("Readset not instanceof SequenceReadsetImpl");
            }


            getPipelineJob().getSequenceSupport().cacheReadset(f.getReadsetId(), getJob().getUser(), handler.supportsSraArchivedData());
        }

        getJob().getLogger().info("total readsets: " + getPipelineJob().getReadsets().size());

        if (getPipelineJob().getParameterJson() != null)
        {
            getJob().getLogger().debug("job parameters:");
            getJob().getLogger().debug(getPipelineJob().getParameterJson().toString(1));
        }

        if (handler instanceof ParameterizedOutputHandler)
        {
            for (ToolParameterDescriptor pd : ((ParameterizedOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>)handler).getParameters())
            {
                if (pd instanceof ToolParameterDescriptor.CachableParam)
                {
                    Object val = getPipelineJob().getParameterJson().opt(pd.getName());
                    ((ToolParameterDescriptor.CachableParam)pd).doCache(getJob(), val, getPipelineJob().getSequenceSupport());
                }
            }
        }

        handler.getProcessor().init(getJob(), getPipelineJob().getSequenceSupport(), getPipelineJob().getReadsets(), getPipelineJob().getParameterJson(), getPipelineJob().getAnalysisDirectory(), actions, outputsToCreate);

        if (!outputsToCreate.isEmpty())
        {
            getJob().getLogger().info(outputsToCreate.size() + " to create");
            for (SequenceOutputFile o : outputsToCreate)
            {
                getPipelineJob().addOutputToCreate(o);
            }
        }

        return new RecordedActionSet(actions);
    }
}
