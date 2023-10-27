package org.labkey.jbrowse.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.List;

/**
 * User: bbimber
 * Date: 8/6/12
 * Time: 12:57 PM
 */
public class JBrowseLuceneTask extends PipelineJob.Task<JBrowseLuceneTask.Factory>
{
    protected JBrowseLuceneTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(JBrowseLuceneTask.class);
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
            return List.of("JBrowse-Lucene");
        }

        @Override
        public PipelineJob.Task<?> createTask(PipelineJob job)
        {
            return new JBrowseLuceneTask(this, job);
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
        JBrowseLucenePipelineJob job = getPipelineJob();
        JBrowseLucenePipelineJob.prepareLuceneIndex(job.getVcf(), job.getTargetDir(), job.getLogger(), job.getInfoFields(), job.isAllowLenientLuceneProcessing());

        return new RecordedActionSet(Collections.singleton(new RecordedAction("JBrowse")));
    }

    private JBrowseLucenePipelineJob getPipelineJob()
    {
        return (JBrowseLucenePipelineJob)getJob();
    }
}
