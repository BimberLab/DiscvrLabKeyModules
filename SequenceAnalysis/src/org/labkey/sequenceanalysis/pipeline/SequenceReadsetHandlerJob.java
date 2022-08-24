package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.HasJobParams;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceReadsetHandlerJob extends SequenceJob implements HasJobParams
{
    private String _handlerClassName;
    private List<Integer> _readsetIds;

    // Default constructor for serialization
    protected SequenceReadsetHandlerJob()
    {
    }

    public SequenceReadsetHandlerJob(Container c, User user, @Nullable String jobName, PipeRoot pipeRoot, SequenceOutputHandler<?> handler, List<SequenceReadsetImpl> readsets, JSONObject jsonParams) throws IOException
    {
        super(SequenceReadsetHandlerPipelineProvider.NAME, c, user, jobName, pipeRoot, jsonParams, null, SequenceOutputHandlerJob.FOLDER_NAME);

        _handlerClassName = handler.getClass().getName();

        //for the purpose of caching files:
        _readsetIds = new ArrayList<>();
        for (SequenceReadsetImpl rs : readsets)
        {
            getSequenceSupport().cacheReadset(rs, getHandler().supportsSraArchivedData());
            _readsetIds.add(rs.getReadsetId());
        }

        writeSupportToDisk();
    }

    @Override
    public TaskPipeline<?> getTaskPipeline()
    {
        return  PipelineJobService.get().getTaskPipeline(new TaskId(SequenceReadsetHandlerJob.class));
    }

    public SequenceOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor> getHandler()
    {
        SequenceOutputHandler handler = SequenceAnalysisManager.get().getFileHandler(_handlerClassName, SequenceOutputHandler.TYPE.Readset);
        if (handler == null)
        {
            throw new IllegalArgumentException("Unable to find handler: " + _handlerClassName);
        }

        return handler;
    }

    public List<Readset> getReadsets()
    {
        return getReadsetIds().stream().map(getSequenceSupport()::getCachedReadset).collect(Collectors.toList());
    }

    @Override
    public List<File> getInputFiles()
    {
        try
        {
            List<File> ret = new ArrayList<>();
            for (Readset rs : getReadsets())
            {
                for (ReadData d : rs.getReadData())
                {
                    if (d.getFile1() != null)
                    {
                        ret.add(d.getFile1());
                    }

                    if (d.getFile2() != null)
                    {
                        ret.add(d.getFile2());
                    }
                }
            }

            return ret;
        }
        catch (Exception e)
        {
            _logger.error(e);
        }

        return null;
    }

    public List<Integer> getReadsetIds()
    {
        return _readsetIds;
    }

    public void setReadsetIds(List<Integer> readsetIds)
    {
        _readsetIds = readsetIds;
    }
}