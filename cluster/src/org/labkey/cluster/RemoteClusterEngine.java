package org.labkey.cluster;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.quartz.JobExecutionException;

/**
 * Created by bimber on 7/11/2017.
 */
public interface RemoteClusterEngine
{
    void doScheduledUpdate() throws JobExecutionException;

    void runTestJob(Container c, User u) throws PipelineJobException;
}
