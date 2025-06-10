package org.labkey.cluster.pipeline;

import org.jetbrains.annotations.NotNull;

/**
 * Created by bimber on 10/31/2015.
 */
public class HTCondorExecutionEngineConfig extends AbstractClusterEngineConfig
{
    @NotNull
    @Override
    public String getType()
    {
        return HTCondorExecutionEngine.TYPE;
    }
}
