package org.labkey.blast.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.files.FileUrls;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.blast.model.BlastJob;

import java.io.File;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class BlastPipelineJob extends PipelineJob
{
    private BlastJob _blastJob;

    public BlastPipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, BlastJob blastJob)
    {
        super(null, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _blastJob = blastJob;
        setLogFile(new File(blastJob.getOutputDir(), "blast-" + getBlastJob().getObjectid() + ".log"));
    }

    @Override
    public String getDescription()
    {
        return "BLAST Query";
    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(BlastPipelineJob.class));
    }

    public BlastJob getBlastJob()
    {
        return _blastJob;
    }
}
