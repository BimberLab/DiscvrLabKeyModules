package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
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
public class SequenceOutputHandlerInitTask extends PipelineJob.Task<SequenceOutputHandlerInitTask.Factory>
{
    protected SequenceOutputHandlerInitTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(SequenceOutputHandlerInitTask.class);
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
            List<String> allowableNames = new ArrayList<>();
            for (SequenceOutputHandler handler : SequenceAnalysisServiceImpl.get().getFileHandlers())
            {
                allowableNames.add(handler.getName());
            }

            return allowableNames;
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SequenceOutputHandlerInitTask(this, job);
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

    public RecordedActionSet run() throws PipelineJobException
    {
        List<RecordedAction> actions = new ArrayList<>();

        SequenceOutputHandler handler = getPipelineJob().getHandler();
        List<SequenceOutputFile> outputsToCreate = new ArrayList<>();

        for (SequenceOutputFile f : getPipelineJob().getFiles())
        {
            f.cacheForRemoteServer();
        }

        handler.getProcessor().init(getJob(), getPipelineJob().getSequenceSupport(), getPipelineJob().getFiles(), getPipelineJob().getJsonParams(), getPipelineJob().getAnalysisDirectory(), actions, outputsToCreate);

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
