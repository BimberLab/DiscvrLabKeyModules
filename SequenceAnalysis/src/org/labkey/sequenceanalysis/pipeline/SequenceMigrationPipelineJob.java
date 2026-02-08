package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileUrls;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.sequenceanalysis.SequenceAnalysisUpgradeCode;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class SequenceMigrationPipelineJob extends PipelineJob
{
    public static class Provider extends PipelineProvider
    {
        public static final String NAME = "sequenceMigrationPipeline";

        public Provider(Module owningModule)
        {
            super(NAME, owningModule);
        }

        @Override
        public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
        {

        }
    }

    // Default constructor for serialization
    protected SequenceMigrationPipelineJob()
    {
    }

    public SequenceMigrationPipelineJob(Container c, User u, ActionURL url, PipeRoot pipeRoot)
    {
        super(Provider.NAME, new ViewBackgroundInfo(c, u, url), pipeRoot);

        File subdir = FileUtil.appendName(pipeRoot.getRootPath(), Provider.NAME);
        if (!subdir.exists())
        {
            subdir.mkdirs();
        }

        setLogFile(FileUtil.appendName(subdir, FileUtil.makeFileNameWithTimestamp("sequenceMigration", "log")));

    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public String getDescription()
    {
        return "Migrate Sequence Files";
    }

    @Override
    public TaskPipeline<?> getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(SequenceMigrationPipelineJob.class));
    }

    public static class Task extends PipelineJob.Task<Task.Factory>
    {
        protected Task(Factory factory, PipelineJob job)
        {
            super(factory, job);
        }

        public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
        {
            public Factory()
            {
                super(Task.class);
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
                return List.of("Migrate Sequence Files");
            }

            @Override
            public PipelineJob.Task<?> createTask(PipelineJob job)
            {
                return new Task(this, job);
            }

            @Override
            public boolean isJobComplete(PipelineJob job)
            {
                return false;
            }
        }

        private SequenceMigrationPipelineJob getPipelineJob()
        {
            return (SequenceMigrationPipelineJob)getJob();
        }

        @Override
        public @NotNull RecordedActionSet run() throws PipelineJobException
        {
            SequenceAnalysisUpgradeCode.doSequenceMigration(getPipelineJob().getUser(), getPipelineJob().getLogger(), -1);

            return new RecordedActionSet();
        }
    }
}
