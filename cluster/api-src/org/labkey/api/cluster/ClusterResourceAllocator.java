package org.labkey.api.cluster;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.pipeline.TaskId;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * CluterResourceAllocator typically serves two functions.  First, it allows code to be written to automatically adjust job resource settings
 * per request, such as by task type, or to make inferences based on size of job inputs.  Second, they provide the ability to write code to
 * modify submissions to handle configuration that might be specific to a particular institution's cluster, beyond what it already configurable
 * in the pipelineConfig.xml file.
 *
 * Created by bimber on 1/25/2016.
 */
public interface ClusterResourceAllocator
{
    public interface Factory
    {
        public ClusterResourceAllocator getAllocator();

        /**
         * Prior to submitting to condor, we iterate all registered ResourceAllocators and query this
         * method, passing the taskId of the active task.  If this allocator can process this task type,
         * it should return a non-null value.  Allocators returning null will be ignored.  Allocators will be iterated in dependency order (highest last)
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
    public void addExtraSubmitScriptLines(PipelineJob job, RemoteExecutionEngine engine, List<String> existingExtraLines);

    /**
     * The memory, in GB, to use as -xmx for the LabKey java remote process
     */
    @Nullable
    default void processJavaOpts(PipelineJob job, RemoteExecutionEngine engine, @NotNull List<String> existingJavaOpts)
    {

    }

    @NotNull
    default Map<String, Object> getEnvironmentVars(PipelineJob job, RemoteExecutionEngine engine)
    {
        return Collections.emptyMap();
    }
}
