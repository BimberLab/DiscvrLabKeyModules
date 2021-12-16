package org.labkey.cluster.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobNotificationProvider;
import org.labkey.cluster.ClusterManager;

public class ClusterPipelineJobNotificationProvider implements PipelineJobNotificationProvider
{
    private static final Logger _log = LogManager.getLogger(ClusterPipelineJobNotificationProvider.class);

    public ClusterPipelineJobNotificationProvider()
    {

    }

    @Override
    public @NotNull String getName()
    {
        return "Cluster Pipeline Notification Provider";
    }

    @Override
    public void onJobQueued(PipelineJob job)
    {
        if (ClusterManager.get().isClusterDebugMode())
        {
            if (job != null)
            {
                job.getLogger().debug("Pipeline job queued: " + job.getJobGUID());
            }
            else
            {
                _log.error("Null pipeline job was queued");
            }
        }
    }
}
