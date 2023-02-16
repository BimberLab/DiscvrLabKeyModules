package org.labkey.sequenceanalysis.pipeline;

import com.google.common.base.Predicates;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenomeManager;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceOutputHandlerRemoteTask extends WorkDirectoryTask<SequenceOutputHandlerRemoteTask.Factory>
{
    protected SequenceOutputHandlerRemoteTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(SequenceOutputHandlerRemoteTask.class);
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
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            if (job instanceof SequenceOutputHandlerJob)
            {
                if (!((SequenceOutputHandlerJob)job).getHandler().doRunRemote())
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
            return new SequenceOutputHandlerRemoteTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private SequenceOutputHandlerJob getPipelineJob()
    {
        return (SequenceOutputHandlerJob)getJob();
    }

    public static void possiblyCacheGenomes(SequenceJob job, List<SequenceOutputFile> inputs) throws PipelineJobException
    {
        if (SequencePipelineService.get().isRemoteGenomeCacheUsed())
        {
            Set<Integer> distinctGenomes = inputs.stream().map(SequenceOutputFile::getLibrary_id).filter(Predicates.notNull()).collect(Collectors.toSet());
            for (Integer l : distinctGenomes)
            {
                ReferenceGenome referenceGenome = job.getSequenceSupport().getCachedGenome(l);
                ReferenceGenomeManager.get().cacheGenomeLocally(referenceGenome, job.getLogger());
            }
        }
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        SequenceTaskHelper.logModuleVersions(getJob().getLogger());

        SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> handler = getPipelineJob().getHandler();
        JobContextImpl ctx = new JobContextImpl(getPipelineJob(), getPipelineJob().getSequenceSupport(), getPipelineJob().getParameterJson(), _wd.getDir(), new TaskFileManagerImpl(getPipelineJob(), _wd.getDir(), _wd), _wd);

        possiblyCacheGenomes(getPipelineJob(), getPipelineJob().getFiles());

        getJob().setStatus(PipelineJob.TaskStatus.running, "Running: " + handler.getName());
        getJob().getLogger().info("Output file IDs: " + getPipelineJob().getFiles().stream().map(SequenceOutputFile::getRowid).map(String::valueOf).collect(Collectors.joining(",")));
        handler.getProcessor().processFilesRemote(getPipelineJob().getFiles(), ctx);

        //Note: on job resume the TaskFileManager could be replaced with one from the resumer
        ctx.getFileManager().deleteIntermediateFiles();
        ctx.getFileManager().cleanup(ctx.getActions());

        return new RecordedActionSet(ctx.getActions());
    }

}
