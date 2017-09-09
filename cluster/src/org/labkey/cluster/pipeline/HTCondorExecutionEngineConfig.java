package org.labkey.cluster.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.util.ArrayList;
import java.util.List;

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
