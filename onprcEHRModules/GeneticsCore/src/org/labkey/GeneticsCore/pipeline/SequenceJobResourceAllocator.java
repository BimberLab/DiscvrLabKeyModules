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
    @Override
    public Integer getPriority(TaskId taskId)
    {
        return (taskId.getNamespaceClass() != null && taskId.getNamespaceClass().getName().equals("org.labkey.sequenceanalysis.pipeline.SequenceAlignmentTask"))  ? 50 : null;
    }

    @Override
    public Integer getMaxRequestCpus(PipelineJob job)
    {
        return null;
    }

    @Override
    public Integer getMaxRequestMemory(PipelineJob job)
    {
        return null;
    }

    @Override
    public List<String> getExtraSubmitScriptLines(PipelineJob job)
    {
        List<File> files = SequencePipelineService.get().getSequenceJobInputFiles(job);
        if (files != null)
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

            //50gb
            if (total > 50e9)
            {
                return Arrays.asList("concurrency_limits = WEEK_LONG_JOBS");
            }
        }

        return null;
    }
}