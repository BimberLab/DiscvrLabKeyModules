package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 5/26/14
 * Time: 1:38 PM
 */
public class FileCleanupTask extends WorkDirectoryTask<FileCleanupTask.Factory>
{
    private static final String ACTIONNAME = "Cleaning Up Files";

    private SequenceTaskHelper _taskHelper;

    protected FileCleanupTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(FileCleanupTask.class);
            setJoin(true);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "CLEANING UP FILES";
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(ACTIONNAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            FileCleanupTask task = new FileCleanupTask(this, job);

            return task;
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private SequenceTaskHelper getHelper()
    {
        return _taskHelper;
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        _taskHelper = new SequenceTaskHelper(job, _wd);

        getJob().getLogger().info("Cleaning up intermediate files");
        RecordedAction action = new RecordedAction(ACTIONNAME);

        getHelper().getFileManager().deleteDeferredIntermediateFiles();

        for (File input : _wd.getDir().listFiles())
        {
            try
            {
                String path = _wd.getRelativePath(input);
                File dest = new File(getHelper().getSupport().getAnalysisDirectory(), path);
                dest = _wd.outputFile(input, dest);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return new RecordedActionSet(action);
    }
}
