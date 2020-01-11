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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VariantProcessingRemoteSplitTask extends WorkDirectoryTask<VariantProcessingRemoteSplitTask.Factory>
{
    protected VariantProcessingRemoteSplitTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(VariantProcessingRemoteSplitTask.class);
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
            for (SequenceOutputHandler handler : SequenceAnalysisServiceImpl.get().getFileHandlers(SequenceOutputHandler.TYPE.OutputFile))
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
        public boolean isJoin()
        {
            return false;
        }

        @Override
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            return super.isParticipant(job);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new VariantProcessingRemoteSplitTask(this, job);
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

        SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> handler = getPipelineJob().getHandler();
        JobContextImpl ctx = new JobContextImpl(getPipelineJob(), getPipelineJob().getSequenceSupport(), getPipelineJob().getParameterJson(), _wd.getDir(), new TaskFileManagerImpl(getPipelineJob(), _wd.getDir(), _wd), _wd);

        getJob().setStatus(PipelineJob.TaskStatus.running, "Running: " + handler.getName());
        handler.getProcessor().processFilesRemote(getPipelineJob().getFiles(), ctx);

        if (getPipelineJob().getContigForTask() != null)
        {
            Set<File> finalVcfs = new HashSet<>();
            TaskFileManagerImpl manager = (TaskFileManagerImpl)ctx.getFileManager();
            manager.getOutputsToCreate().forEach(x ->  {
                if ("VCF File".equals(x.getCategory()))
                {
                    finalVcfs.add(x.getFile());
                }
            });

            if (finalVcfs.isEmpty())
            {
                throw new PipelineJobException("Unable to find final VCF");
            }
            else if (finalVcfs.size() > 1)
            {
                throw new PipelineJobException("More than one output tagged as final VCF");
            }

            getPipelineJob().getFinalVCFs().put(getPipelineJob().getContigForTask(), finalVcfs.iterator().next());
        }

        //Note: on job resume the TaskFileManager could be replaced with one from the resumer
        ctx.getFileManager().deleteIntermediateFiles();
        ctx.getFileManager().cleanup(ctx.getActions());

        return new RecordedActionSet(ctx.getActions());
    }
}
