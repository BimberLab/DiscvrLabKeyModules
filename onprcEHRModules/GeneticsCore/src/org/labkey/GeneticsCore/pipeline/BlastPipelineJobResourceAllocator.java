package org.labkey.GeneticsCore.pipeline;

import org.labkey.api.htcondorconnector.HTCondorJobResourceAllocator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskId;

import java.util.List;

/**
 * Created by bimber on 3/9/2016.
 */
public class BlastPipelineJobResourceAllocator implements HTCondorJobResourceAllocator
{
    @Override
    public Integer getPriority(TaskId taskId)
    {
        return (taskId.getNamespaceClass() != null && taskId.getNamespaceClass().getName().equals("org.labkey.blast.pipeline.BlastWorkTask"))  ? 50 : null;
    }

    @Override
    public Integer getMaxRequestCpus(PipelineJob job)
    {
        return 2;
    }

    @Override
    public Integer getMaxRequestMemory(PipelineJob job)
    {
        return null;
    }

    @Override
    public List<String> getExtraSubmitScriptLines(PipelineJob job)
    {
        return null;
    }
}