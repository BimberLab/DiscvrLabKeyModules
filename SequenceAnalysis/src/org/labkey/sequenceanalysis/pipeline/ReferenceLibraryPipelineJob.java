package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.files.FileUrls;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class ReferenceLibraryPipelineJob extends PipelineJob
{
    private String _name;
    private String _description;
    private List<Integer> _sequenceIds;

    public ReferenceLibraryPipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, String name, String description, List<Integer> sequenceIds) throws IOException
    {
        super(null, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _name = name;
        _description = description;
        _sequenceIds = sequenceIds;

        File outputDir = getOutputDir();
        setLogFile(new File(outputDir, FileUtil.makeFileNameWithTimestamp("referenceLibrary", "log")));
    }

    @Override
    public String getDescription()
    {
        return "Create Reference Library";
    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(ReferenceLibraryPipelineJob.class));
    }

    public String getName()
    {
        return _name;
    }

    public String getLibraryDescription()
    {
        return _description;
    }

    public List<Integer> getSequenceIds()
    {
        return _sequenceIds;
    }

    public File getOutputDir() throws IOException
    {
        File pipelineDir = PipelineService.get().getPipelineRootSetting(getContainer()).getRootPath();
        if (pipelineDir == null)
        {
            throw new IOException("No pipeline directory set for folder: " + getContainer().getPath());
        }

        File outputDir = new File(pipelineDir, ".referenceLibraries");
        if (!outputDir.exists())
        {
            outputDir.mkdirs();
        }

        return outputDir;
    }
}
