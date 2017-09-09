package org.labkey.cluster.pipeline;

import org.jetbrains.annotations.NotNull;

/**
 * Created by bimber on 7/11/2017.
 */
public class SlurmExecutionEngineConfig extends AbstractClusterEngineConfig
{
    @NotNull
    @Override
    public String getType()
    {
        return SlurmExecutionEngine.TYPE;
    }
}
