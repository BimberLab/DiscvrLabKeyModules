package org.labkey.GeneticsCore.pipeline;

import org.labkey.api.cluster.ClusterResourceAllocator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.pipeline.TaskId;

import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 3/9/2016.
 */
public class BlastPipelineJobResourceAllocator implements ClusterResourceAllocator
{
    public static class Factory implements ClusterResourceAllocator.Factory
    {
        @Override
        public ClusterResourceAllocator getAllocator()
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
    public void addExtraSubmitScriptLines(PipelineJob job, RemoteExecutionEngine engine, List<String> lines)
    {
        //force BLAST jobs to top of queue, since we assume these run quickly
        if ("HTCondorEngine".equals(engine.getType()))
        {
            lines.add("+JobPrio = 5");
        }
        else if ("SlurmEngine".equals(engine.getType()))
        {
            //TODO?
        }
    }
}