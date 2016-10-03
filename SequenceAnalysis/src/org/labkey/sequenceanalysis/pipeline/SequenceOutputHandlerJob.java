package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.HasJobParams;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceOutputHandlerJob extends SequenceJob implements HasJobParams
{
    public static final String FOLDER_NAME = "sequenceOutput";

    private String _handlerClassName;
    private List<SequenceOutputFile> _files;

    public SequenceOutputHandlerJob(Container c, User user, @Nullable String jobName, PipeRoot pipeRoot, SequenceOutputHandler handler, List<SequenceOutputFile> files, JSONObject jsonParams) throws IOException
    {
        super(SequenceOutputHandlerPipelineProvider.NAME, c, user, jobName, pipeRoot, jsonParams, null, FOLDER_NAME);

        _handlerClassName = handler.getClass().getName();
        _files = files;

        //for the purpose of caching files:
        for (SequenceOutputFile o : _files)
        {
            o.getFile();
        }
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return  PipelineJobService.get().getTaskPipeline(new TaskId(SequenceOutputHandlerJob.class));
    }

    public SequenceOutputHandler getHandler()
    {
        SequenceOutputHandler handler = SequenceAnalysisManager.get().getFileHandler(_handlerClassName);
        if (handler == null)
        {
            throw new IllegalArgumentException("Unable to find handler: " + _handlerClassName);
        }

        return handler;
    }

    public List<SequenceOutputFile> getFiles()
    {
        return _files;
    }

    @Override
    public List<File> getInputFiles()
    {
        List<File> ret = new ArrayList<>();
        for (SequenceOutputFile o : _files)
        {
            ret.add(o.getFile());
        }

        return ret;
    }
}