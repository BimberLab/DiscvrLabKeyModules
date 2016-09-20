package org.labkey.GeneticsCore.pipeline;

import org.apache.commons.io.FileUtils;
import org.labkey.api.htcondorconnector.HTCondorJobResourceAllocator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 *
 * Created by bbimber
 *
 */
public class SequenceJobResourceAllocator implements HTCondorJobResourceAllocator
{
    public static class Factory implements HTCondorJobResourceAllocator.Factory
    {
        @Override
        public HTCondorJobResourceAllocator getAllocator()
        {
            return new SequenceJobResourceAllocator();
        }

        @Override
        public Integer getPriority(TaskId taskId)
        {
            return (taskId.getNamespaceClass() != null && (
                    taskId.getNamespaceClass().getName().equals("org.labkey.sequenceanalysis.pipeline.SequenceAlignmentTask") ||
                    taskId.getNamespaceClass().getName().equals("org.labkey.sequenceanalysis.pipeline.SequenceNormalizationTask")
            ))  ? 50 : null;
        }
    }

    private boolean isSequenceNormalizationTask(PipelineJob job)
    {
        return (job.getActiveTaskId() != null && job.getActiveTaskId().getNamespaceClass().getName().endsWith("SequenceNormalizationTask"));
    }

    private Long _totalFileSize = null;
    private static final Long UNABLE_TO_DETERMINE = -1L;

    @Override
    public Integer getMaxRequestCpus(PipelineJob job)
    {
        if (isSequenceNormalizationTask(job))
        {
            job.getLogger().debug("setting max CPUs to 4");
            return 4;
        }

        Long totalFileSize = getFileSize(job);
        if (UNABLE_TO_DETERMINE.equals(totalFileSize))
        {
            return null;
        }

        //10gb
        if (totalFileSize < 10e9)
        {
            job.getLogger().debug("file size less than 10gb, lowering CPUs to 8");

            return 8;
        }
        else if (totalFileSize < 20e9)
        {
            job.getLogger().debug("file size less than 20gb, lowering CPUs to 16");

            return 16;
        }

        return null;
    }

    @Override
    public Integer getMaxRequestMemory(PipelineJob job)
    {
        if (isSequenceNormalizationTask(job))
        {
            job.getLogger().debug("setting memory to 24");
            return 24;
        }

        Long totalFileSize = getFileSize(job);
        if (UNABLE_TO_DETERMINE.equals(totalFileSize))
        {
            return null;
        }

        if (totalFileSize < 10e9)
        {
            job.getLogger().debug("file size less than 10gb, lowering memory to 16");

            return 16;
        }
        else if (totalFileSize < 20e9)
        {
            job.getLogger().debug("file size less than 20gb, lowering memory to 24");

            return 24;
        }

        return null;
    }

    @Override
    public List<String> getExtraSubmitScriptLines(PipelineJob job)
    {
        Long totalFileSize = getFileSize(job);
        if (UNABLE_TO_DETERMINE.equals(totalFileSize))
        {
            return null;
        }

        //30gb
        if (totalFileSize > 30e9)
        {
            return Arrays.asList("concurrency_limits = WEEK_LONG_JOBS");
        }

        return null;
    }

    private Long getFileSize(PipelineJob job)
    {
        if (_totalFileSize != null)
        {
            return _totalFileSize;
        }

        List<File> files = SequencePipelineService.get().getSequenceJobInputFiles(job);
        if (files != null && !files.isEmpty())
        {
            long total = 0;
            for (File f : files)
            {
                if (f.exists())
                {
                    total += f.length();
                }
            }

            job.getLogger().info("total size of input files: " + FileUtils.byteCountToDisplaySize(total));

            _totalFileSize = total;
        }
        else
        {
            _totalFileSize = UNABLE_TO_DETERMINE;
        }

        return _totalFileSize;
    }
}