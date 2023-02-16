package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceReadsetHandlerRemoteTask extends WorkDirectoryTask<SequenceReadsetHandlerRemoteTask.Factory>
{
    protected SequenceReadsetHandlerRemoteTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(SequenceReadsetHandlerRemoteTask.class);
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

                if (handler instanceof SequenceOutputHandler.HasActionNames)
                {
                    allowableNames.addAll(((SequenceOutputHandler.HasActionNames)handler).getAllowableActionNames());
                }
            }

            return allowableNames;
        }

        @Override
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            if (job instanceof SequenceReadsetHandlerJob)
            {
                if (!((SequenceReadsetHandlerJob)job).getHandler().doRunRemote())
                {
                    job.getLogger().info("skipping remote task");
                    return false;
                }
            }

            return super.isParticipant(job);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SequenceReadsetHandlerRemoteTask(this, job);
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

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        SequenceOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor> handler = getPipelineJob().getHandler();
        JobContextImpl ctx = new JobContextImpl(getPipelineJob(), getPipelineJob().getSequenceSupport(), getPipelineJob().getParameterJson(), _wd.getDir(), new TaskFileManagerImpl(getPipelineJob(), _wd.getDir(), _wd), _wd);

        getJob().setStatus(PipelineJob.TaskStatus.running, "Running: " + handler.getName());
        handler.getProcessor().processFilesRemote(getPipelineJob().getReadsets(), ctx);

        //Note: on job resume the TaskFileManager could be replaced with one from the resumer
        ctx.getFileManager().deleteIntermediateFiles();
        ctx.getFileManager().cleanup(ctx.getActions());

        return new RecordedActionSet(ctx.getActions());
    }

}
