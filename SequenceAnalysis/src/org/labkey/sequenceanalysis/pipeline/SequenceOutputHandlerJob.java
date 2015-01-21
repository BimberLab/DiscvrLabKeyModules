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
import org.labkey.api.sequenceanalysis.SequenceOutputHandler;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceOutputHandlerJob extends PipelineJob implements FileAnalysisJobSupport
{
    private SequenceOutputHandler _handler;
    private JSONObject _jsonParams;
    private List<SequenceOutputFile> _files;
    private List<SequenceOutputFile> _outputsCreated = new ArrayList<>();
    private File _outDir;
    private Integer _experimentRunRowId;

    public SequenceOutputHandlerJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, SequenceOutputHandler handler, List<SequenceOutputFile> files, JSONObject jsonParams)
    {
        super(SequenceOutputHandlerPipelineProvider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);

        _handler = handler;
        _jsonParams = jsonParams;
        _files = files;

        _outDir = new File(pipeRoot.getRootPath(), "sequenceOutputs");
        if (!_outDir.exists())
        {
            _outDir.mkdir();
        }

        AssayFileWriter writer = new AssayFileWriter();
        String protocolName = FileUtil.makeLegalName("sequenceOutput_" + FileUtil.getTimestamp());

        _outDir = writer.findUniqueFileName(protocolName, _outDir);
        if (!_outDir.exists())
        {
            _outDir.mkdir();
        }

        setLogFile(new File(_outDir, "output.log"));
    }

    @Override
    public String getDescription()
    {
        return _handler.getDescription() == null ? _handler.getName() : _handler.getDescription();
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
        return PipelineJobService.get().getTaskPipeline(new TaskId(SequenceOutputHandlerJob.class));
    }

    public SequenceOutputHandler getHandler()
    {
        return _handler;
    }

    public JSONObject getJsonParams()
    {
        return _jsonParams;
    }

    public List<SequenceOutputFile> getFiles()
    {
        return _files;
    }

    public void addOutputCreated(SequenceOutputFile o)
    {
        _outputsCreated.add(o);
    }

    public List<SequenceOutputFile> getOutputsCreated()
    {
        return _outputsCreated;
    }

    @Override
    public String getProtocolName()
    {
        return null;
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
        return null;
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
}