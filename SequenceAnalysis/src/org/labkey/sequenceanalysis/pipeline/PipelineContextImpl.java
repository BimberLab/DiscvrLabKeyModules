package org.labkey.sequenceanalysis.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;

import java.io.File;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 7:43 AM
 */
public class PipelineContextImpl implements PipelineContext
{
    private PipelineJob _job;
    private File _workingDirectory;

    public PipelineContextImpl(PipelineJob job, File workingDirectory)
    {
        _job = job;
        _workingDirectory = workingDirectory;
    }

    @Override
    public Logger getLogger()
    {
        return _job.getLogger();
    }

    @Override
    public PipelineJob getJob()
    {
        return _job;
    }

    @Override
    public WorkDirectory getWorkDir()
    {
        return null;
    }

    @Override
    public SequenceAnalysisJobSupport getSequenceSupport()
    {
        return (SequenceAnalysisJobSupport)_job;
    }

    public FileAnalysisJobSupport getSupport()
    {
        return (FileAnalysisJobSupport)_job;
    }

    @Override
    public File getWorkingDirectory()
    {
        return _workingDirectory;
    }

    @Override
    public File getSourceDirectory()
    {
        return getSupport().getAnalysisDirectory();
    }
}
