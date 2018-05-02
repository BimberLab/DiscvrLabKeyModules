package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ViewContext;

/**
 * Created by bimber on 1/5/2015.
 */
public class SequenceReadsetHandlerPipelineProvider extends PipelineProvider
{
    public static final String NAME = "sequenceReadsetHandler";

    public SequenceReadsetHandlerPipelineProvider(Module owningModule)
    {
        super(NAME, owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {

    }
}
