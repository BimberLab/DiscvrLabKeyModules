package org.labkey.blast.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
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
    private File _dbDir;
    private File _binDir;

    public BlastPipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, BlastJob blastJob, File dbDir, File binDir)
    {
        super(BlastPipelineProvider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _blastJob = blastJob;
        _dbDir = dbDir;
        _binDir = binDir;
        //NOTE: it is imported to call blastJob.getOutputDir() so this value is cached for remote servers
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
        if (_blastJob != null && _blastJob.getObjectid() != null)
        {
            return DetailsURL.fromString("/blast/jobDetails.view?jobId=" + _blastJob.getJobId(), getContainer()).getActionURL();
        }

        return null;
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

    public File getDbDir()
    {
        return _dbDir;
    }

    public File getBinDir()
    {
        if (_binDir != null)
        {
            return _binDir;
        }
        else
        {
            return new File(PipelineJobService.get().getAppProperties().getToolsDirectory());
        }
    }
}
