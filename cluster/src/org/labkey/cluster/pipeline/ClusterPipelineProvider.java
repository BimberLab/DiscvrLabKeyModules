package org.labkey.cluster.pipeline;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ViewContext;

/**
 * Created by bimber on 7/27/2017.
 */
public class ClusterPipelineProvider extends PipelineProvider
{
    public static final String NAME = "Cluster Pipeline";

    public ClusterPipelineProvider(Module owningModule)
    {
        super(NAME, owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {

    }
}
