package org.labkey.api.cluster;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.security.User;

import java.io.File;
import java.io.Serializable;

/**
 * Created by bimber on 2/23/2016.
 */
abstract public class ClusterService
{
    private static ClusterService _instance;

    public static ClusterService get()
    {
        return _instance;
    }

    public static void setInstance(ClusterService instance)
    {
        _instance = instance;
    }

    abstract public void registerResourceAllocator(ClusterResourceAllocator.Factory allocator);

    /**
     * This creates a barebones PipelineJob configured to run on the selected RemoteExecutionEngine.
     * You may wish to further configure this job.  This method does not submit the job, which you can do using PipelineService.queueJob()
     */
    abstract public PipelineJob createClusterRemotePipelineJob(Container c, User u, String jobName, RemoteExecutionEngine engine, ClusterRemoteTask task, File logFile) throws PipelineValidationException;

    public interface ClusterRemoteTask extends Serializable
    {
        public void run(Logger log);
    }
}
