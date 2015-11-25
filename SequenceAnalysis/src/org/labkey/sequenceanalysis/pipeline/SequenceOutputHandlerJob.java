package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceOutputHandlerJob extends PipelineJob implements FileAnalysisJobSupport
{
    private String _handlerClassName;
    private String _protocolName;
    private JSONObject _jsonParams;
    private List<SequenceOutputFile> _files;
    private List<SequenceOutputFile> _outputsToCreate = new ArrayList<>();
    private File _outDir;
    private Integer _experimentRunRowId;
    private SequenceJobSupportImpl _support;

    public SequenceOutputHandlerJob(Container c, User user, ActionURL url, @Nullable String jobName, PipeRoot pipeRoot, SequenceOutputHandler handler, List<SequenceOutputFile> files, JSONObject jsonParams)
    {
        super(SequenceOutputHandlerPipelineProvider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _support = new SequenceJobSupportImpl();
        String timestamp = FileUtil.getTimestamp();
        _protocolName = (jobName == null ? handler.getName() : jobName) + "_" + timestamp;
        _handlerClassName = handler.getClass().getName();
        _jsonParams = jsonParams;
        _files = files;

        _outDir = new File(pipeRoot.getRootPath(), "sequenceOutputPipeline");
        if (!_outDir.exists())
        {
            _outDir.mkdir();
        }

        AssayFileWriter writer = new AssayFileWriter();
        String protocolName = FileUtil.makeLegalName("sequenceOutput_" + timestamp);

        _outDir = writer.findUniqueFileName(protocolName, _outDir);
        if (!_outDir.exists())
        {
            _outDir.mkdir();
        }

        //for the purpose of caching files:
        for (SequenceOutputFile o : _files)
        {
            o.getFile();
        }

        setLogFile(new File(_outDir, "output.log"));
    }

    @Override
    public String getDescription()
    {
        return _protocolName;
    }

    @Override
    public ActionURL getStatusHref()
    {
        if (_experimentRunRowId != null)
        {
            ExpRun run = ExperimentService.get().getExpRun(_experimentRunRowId.intValue());
            if (run != null)
                return PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(run);
        }
        return null;
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

    public JSONObject getJsonParams()
    {
        return _jsonParams;
    }

    public List<SequenceOutputFile> getFiles()
    {
        return _files;
    }

    public void addOutputToCreate(SequenceOutputFile o)
    {
        _outputsToCreate.add(o);
    }

    public List<SequenceOutputFile> getOutputsToCreate()
    {
        return _outputsToCreate;
    }

    @Override
    public String getProtocolName()
    {
        return _protocolName;
    }

    @Override
    public String getJoinedBaseName()
    {
        return null;
    }

    @Override
    public List<String> getSplitBaseNames()
    {
        return null;
    }

    @Override
    public String getBaseName()
    {
        return _outDir.getName();
    }

    @Override
    public File getDataDirectory()
    {
        return _outDir;
    }

    @Override
    public File getAnalysisDirectory()
    {
        return _outDir;
    }

    @Override
    public File findInputFile(String name)
    {
        return null;
    }

    @Override
    public File findOutputFile(String name)
    {
        return null;
    }

    @Override
    public File findOutputFile(@NotNull String outputDir, @NotNull String fileName)
    {
        return null;
    }

    @Override
    public ParamParser createParamParser()
    {
        return null;
    }

    @Override
    public File getParametersFile()
    {
        return null;
    }

    @Nullable
    @Override
    public File getJobInfoFile()
    {
        return null;
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

    @Override
    public FileType.gzSupportLevel getGZPreference()
    {
        return null;
    }

    public Integer getExperimentRunRowId()
    {
        return _experimentRunRowId;
    }

    public void setExperimentRunRowId(Integer experimentRunRowId)
    {
        _experimentRunRowId = experimentRunRowId;
    }

    public SequenceAnalysisJobSupport getSequenceSupport()
    {
        return _support;
    }
}