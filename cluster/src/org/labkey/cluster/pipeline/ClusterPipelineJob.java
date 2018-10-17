package org.labkey.cluster.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cluster.ClusterService;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.TaskPipelineSettings;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.cluster.ClusterModule;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by bimber on 7/16/2017.
 */
public class ClusterPipelineJob extends PipelineJob
{
    private String _location;
    private TaskId _taskPipelineId;
    private ClusterService.ClusterRemoteTask _runnable;
    private String _description;

    // Default constructor for serialization
    protected ClusterPipelineJob()
    {
    }

    private ClusterPipelineJob(Container c, User user, PipeRoot pipeRoot, String description, TaskId taskPipelineId, ClusterService.ClusterRemoteTask runnable, File logFile, String location) throws PipelineValidationException
    {
        super(ClusterPipelineProvider.NAME, new ViewBackgroundInfo(c, user, null), pipeRoot);
        setLogFile(logFile);

        _taskPipelineId = taskPipelineId;
        _runnable = runnable;
        _description = description;
        _location = location;
    }

    public static ClusterPipelineJob createJob(Container c, User user, String jobName, ClusterService.ClusterRemoteTask runnable, RemoteExecutionEngine engine, File logFile) throws PipelineValidationException
    {
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                throw new PipelineValidationException("Unable to create log file: " + logFile.getPath());
            }
        }

        return new ClusterPipelineJob(c, user, PipelineService.get().getPipelineRootSetting(c), jobName, getTaskIdForEngine(engine.getConfig().getLocation()), runnable, logFile, engine.getConfig().getLocation());
    }

    @Nullable
    @Override
    public TaskId getActiveTaskId()
    {
        //ensure this TaskFactory is registered:

        try
        {
            TaskId taskFactoryId = getTaskFactoryId(_location);
            try
            {
                if (PipelineJobService.get().getTaskFactory(taskFactoryId) == null)
                {
                    registerTaskPipeline(_location);
                }
            }
            catch (NullPointerException e)
            {
                //this indicates the TaskFactory has not been registered yet
                getLogger().error("A NullPointerException was throw in ClusterPipelineJob", e);
                registerTaskPipeline(_location);
            }
        }
        catch (CloneNotSupportedException e)
        {
            getLogger().error(e.getMessage(), e);
        }

        return super.getActiveTaskId();
    }

    private static TaskId getTaskIdForEngine(String location)
    {
        return new TaskId(ClusterPipelineJob.class, location);
    }

    private static TaskId getTaskFactoryId(String location)
    {
        return new TaskId(ClusterTaskFactory.class, location);
    }

    public static TaskId registerTaskPipeline(String location) throws CloneNotSupportedException
    {
        //first register TaskFactory
        TaskId taskFactoryId = getTaskFactoryId(location);
        PipelineJobService.get().addTaskFactory(new ClusterTaskFactory(taskFactoryId, location));

        //then TaskPipeline
        TaskId taskPipelineId = getTaskIdForEngine(location);
        TaskPipelineSettings settings = new TaskPipelineSettings(taskPipelineId);
        settings.setTaskProgressionSpec(new Object[]{taskFactoryId});
        settings.setDeclaringModule(ModuleLoader.getInstance().getModule(ClusterModule.class));
        PipelineJobService.get().addTaskPipeline(settings);

        return taskPipelineId;
    }

    public ClusterService.ClusterRemoteTask getRunnable()
    {
        return _runnable;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(_taskPipelineId);
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    private static class ClusterTaskFactory extends AbstractTaskFactory<AbstractTaskFactorySettings, ClusterTaskFactory>
    {
        public ClusterTaskFactory(TaskId id, String location)
        {
            super(id);

            setLocation(location);
        }

        @Override
        public Task createTask(PipelineJob job)
        {
            return new Task<ClusterTaskFactory>(this, job)
            {
                @NotNull
                @Override
                public RecordedActionSet run() throws PipelineJobException
                {
                    ((ClusterPipelineJob)getJob()).getRunnable().run(getJob().getLogger());

                    return new RecordedActionSet();
                }
            };
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return null;
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return null;
        }

        @Override
        public String getStatusName()
        {
            return "RUNNING";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
