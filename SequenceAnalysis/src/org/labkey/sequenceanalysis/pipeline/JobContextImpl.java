package org.labkey.sequenceanalysis.pipeline;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * Created by bimber on 8/31/2016.
 */
public class JobContextImpl implements SequenceOutputHandler.JobContext
{
    private SequenceJob _job;
    private SequenceAnalysisJobSupport _support;
    private JSONObject _params;
    private File _outputDir;
    private LinkedHashSet<RecordedAction> _actions = new LinkedHashSet<>();
    private TaskFileManager _fileManager;
    private WorkDirectory _wd;

    public JobContextImpl(SequenceJob job, SequenceAnalysisJobSupport support, JSONObject params, File outputDir, TaskFileManager fileManager, @Nullable WorkDirectory workDirectory)
    {
        _job = job;
        _support = support;
        _params = params;
        _outputDir = outputDir;
        _fileManager = fileManager;
        _wd = workDirectory;

    }

    @Override
    public PipelineJob getJob()
    {
        return _job;
    }

    @Override
    public SequenceAnalysisJobSupport getSequenceSupport()
    {
        return _support;
    }

    @Override
    public JSONObject getParams()
    {
        return _params;
    }

    @Override
    public File getOutputDir()
    {
        return _outputDir;
    }

    @Override
    public void addActions(RecordedAction... actions)
    {
        _actions.addAll(Arrays.asList(actions));
    }

    @Override
    public TaskFileManager getFileManager()
    {
        return _fileManager;
    }

    public void setFileManager(TaskFileManager fileManager)
    {
        _fileManager = fileManager;
    }

    @Override
    public void addSequenceOutput(SequenceOutputFile o)
    {
        _fileManager.addSequenceOutput(o);
    }

    public PipeRoot getFolderPipeRoot()
    {
        return _job.getFolderPipeRoot();
    }

    @Override
    public Logger getLogger()
    {
        return getJob().getLogger();
    }

    @Override
    public WorkDirectory getWorkDir()
    {
        return _wd;
    }

    @Override
    public File getWorkingDirectory()
    {
        return _wd.getDir();
    }

    @Override
    public File getSourceDirectory()
    {
        return getSourceDirectory(false);
    }

    @Override
    public File getSourceDirectory(boolean forceParent)
    {
        return ((SequenceJob)getJob()).getWebserverDir(forceParent);
    }

    public LinkedHashSet<RecordedAction> getActions()
    {
        return _actions;
    }
}
