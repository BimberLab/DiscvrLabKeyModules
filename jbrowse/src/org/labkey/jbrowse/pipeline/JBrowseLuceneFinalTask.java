package org.labkey.jbrowse.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;
import org.labkey.jbrowse.JBrowseLuceneSearch;

import java.util.Collections;
import java.util.List;

/**
 * User: bbimber
 * Date: 8/6/12
 * Time: 12:57 PM
 */
public class JBrowseLuceneFinalTask extends PipelineJob.Task<JBrowseLuceneFinalTask.Factory>
{
    protected JBrowseLuceneFinalTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(JBrowseLuceneFinalTask.class);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return List.of("JBrowse-Lucene-Finalize");
        }

        @Override
        public PipelineJob.Task<?> createTask(PipelineJob job)
        {
            return new JBrowseLuceneFinalTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
        {
            throw new PipelineJobException("This task must run on the webserver!");
        }

        JBrowseLuceneSearch.clearCache(getPipelineJob().getJbrowseTrackId());
        return new RecordedActionSet(Collections.singleton(new RecordedAction("JBrowse-Lucene")));
    }

    private JBrowseLucenePipelineJob getPipelineJob()
    {
        return (JBrowseLucenePipelineJob)getJob();
    }
}
