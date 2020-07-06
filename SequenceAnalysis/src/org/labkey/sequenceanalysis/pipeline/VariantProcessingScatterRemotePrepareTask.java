package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.util.FileType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VariantProcessingScatterRemotePrepareTask extends WorkDirectoryTask<VariantProcessingScatterRemotePrepareTask.Factory>
{
    private static final String ACTION_NAME = "Prepare Scatter/Gather";

    protected VariantProcessingScatterRemotePrepareTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(VariantProcessingScatterRemotePrepareTask.class);
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
            List<String> allowableNames = new ArrayList<>();
            allowableNames.add(ACTION_NAME);

            return allowableNames;
        }

        @Override
        public boolean isJoin()
        {
            return true;
        }

        @Override
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            if (job instanceof VariantProcessingJob)
            {
                VariantProcessingJob vpj = (VariantProcessingJob)job;
                if (!vpj.isScatterJob())
                {
                    job.getLogger().info("Skipping: " + ACTION_NAME);
                    return false;
                }
                else
                {
                    if (vpj.getHandler() instanceof VariantProcessingStep.MayRequirePrepareTask)
                    {
                        return ((VariantProcessingStep.MayRequirePrepareTask)vpj.getHandler()).isRequired(vpj);
                    }
                }
            }

            return false;
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new VariantProcessingScatterRemotePrepareTask(this, job);
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private VariantProcessingJob getPipelineJob()
    {
        return (VariantProcessingJob)getJob();
    }

    @Override
    public @NotNull RecordedActionSet run() throws PipelineJobException
    {
        SequenceTaskHelper.logModuleVersions(getJob().getLogger());

        VariantProcessingJob variantJob = getPipelineJob();
        SequenceOutputHandler handler = variantJob.getHandler();

        if (!( handler instanceof VariantProcessingStep.MayRequirePrepareTask))
        {
            throw new PipelineJobException("This handler does not implement MayRequirePrepareTask: " + handler.getName());
        }

        JobContextImpl ctx = new JobContextImpl(getPipelineJob(), getPipelineJob().getSequenceSupport(), getPipelineJob().getParameterJson(), _wd.getDir(), new TaskFileManagerImpl(getPipelineJob(), _wd.getDir(), _wd), _wd);

        ((VariantProcessingStep.MayRequirePrepareTask)handler).doWork(getPipelineJob().getFiles(), ctx);

        //Note: on job resume the TaskFileManager could be replaced with one from the resumer
        //Also, this needs to run after the step above to manage SequenceOutputFiles
        ctx.getFileManager().deleteIntermediateFiles();
        ctx.getFileManager().cleanup(ctx.getActions());

        return new RecordedActionSet(ctx.getActions());
    }


}
