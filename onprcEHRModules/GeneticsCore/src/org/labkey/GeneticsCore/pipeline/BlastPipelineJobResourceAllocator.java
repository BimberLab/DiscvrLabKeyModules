package org.labkey.GeneticsCore.pipeline;

import org.labkey.api.htcondorconnector.HTCondorJobResourceAllocator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskId;

import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 3/9/2016.
 */
public class BlastPipelineJobResourceAllocator implements HTCondorJobResourceAllocator
{
    public static class Factory implements HTCondorJobResourceAllocator.Factory
    {
        @Override
        public HTCondorJobResourceAllocator getAllocator()
        {
            return new BlastPipelineJobResourceAllocator();
        }

        @Override
        public Integer getPriority(TaskId taskId)
        {
            return (taskId.getNamespaceClass() != null && taskId.getNamespaceClass().getName().equals("org.labkey.blast.pipeline.BlastWorkTask"))  ? 50 : null;
        }
    }

    @Override
    public Integer getMaxRequestCpus(PipelineJob job)
    {
        return 2;
    }

    @Override
    public Integer getMaxRequestMemory(PipelineJob job)
    {
        return 24;
    }

    @Override
    public List<String> getExtraSubmitScriptLines(PipelineJob job)
    {
        //force BLAST jobs to top of queue, since we assume these run quickly
        return Collections.singletonList("+JobPrio = 5");
    }
}