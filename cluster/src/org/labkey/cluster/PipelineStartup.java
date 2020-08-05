package org.labkey.cluster;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.cluster.ClusterService;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.cluster.pipeline.ClusterPipelineJob;

/**
 * Created by bimber on 7/26/2017.
 */
public class PipelineStartup
{
    private static final Logger _log = LogManager.getLogger(PipelineStartup.class);
    private static boolean _hasRegistered = false;

    public PipelineStartup()
    {
        if (_hasRegistered)
        {
            _log.warn("cluster resources have already been registered, skipping");
        }
        else
        {
            _log.info("Registering cluster pipeline steps");
            ClusterService.setInstance(new ClusterServiceImpl());
            registerPipelineSteps();
            _hasRegistered = true;
        }
    }

    private void registerPipelineSteps()
    {
        if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
        {
            for (RemoteExecutionEngine engine : PipelineJobService.get().getRemoteExecutionEngines())
            {
                try
                {
                    _log.info("registering cluster pipeline for: " + engine.getType() + ", " + engine.getConfig().getLocation());
                    ClusterPipelineJob.registerTaskPipeline(engine.getConfig().getLocation());
                }
                catch (Exception e)
                {
                    _log.error("Unable to register task pipeline for engine: " + engine.getType(), e);
                }
            }
        }
        else if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.RemoteServer)
        {
            _log.info("registering cluster pipeline for: " + PipelineJobService.get().getLocationType().name());
            String location = PipelineJobService.get().getRemoteServerProperties().getLocation();
            try
            {
                ClusterPipelineJob.registerTaskPipeline(location);
            }
            catch (Exception e)
            {
                _log.error("Unable to register task pipeline for: " + location, e);
            }
        }
        else if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.RemoteExecutionEngine)
        {
            try
            {
                //technically this location is incorrect, but this should not matter on a remote server
                ClusterPipelineJob.registerTaskPipeline(TaskFactory.WEBSERVER);
            }
            catch (Exception e)
            {
                _log.error("Unable to register task pipeline on remote server", e);
            }
        }
    }
}
