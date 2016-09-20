package org.labkey.api.htcondorconnector;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskId;

import java.util.List;

/**
 * This is a crude start.  Eventually this should be registered per task, not per pipeline job.
 *
 * Created by bimber on 1/25/2016.
 */
public interface HTCondorJobResourceAllocator
{
    public interface Factory
    {
        public HTCondorJobResourceAllocator getAllocator();

        /**
         * Prior to submitting to condor, we iterate all registered ResourceAllocators and query this
         * method, passing the taskId of the active task.  If this allocator can process this task type,
         * it should return a non-null value.  Allocators returning null will be ignored.  The highest priority allocator will be used.
         * Note: because allocators are registered during module startup, this should reflect module dependency order.  This means child modules should
         * take priority over parent modules in the case of a priority tie.
         */
        @Nullable
        public Integer getPriority(TaskId taskId);
    }

    /**
     * The maximum CPUs to request for this job
     */
    @Nullable
    public Integer getMaxRequestCpus(PipelineJob job);

    /**
     * The maximum RAM in GBs to request for this job.  Not currently implemented
     */
    @Nullable
    public Integer getMaxRequestMemory(PipelineJob job);

    /**
     * Additional lines to include in the condor submit script.  These will be appended to the default script.
     */
    @Nullable
    public List<String> getExtraSubmitScriptLines(PipelineJob job);
}
