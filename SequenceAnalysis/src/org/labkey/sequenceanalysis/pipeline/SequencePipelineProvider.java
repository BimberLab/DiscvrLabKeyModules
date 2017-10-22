package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;

/**
 * Created by bimber on 1/5/2015.
 */
public class SequencePipelineProvider extends PipelineProvider
{
    public static final String NAME = "Sequence Pipeline";

    public SequencePipelineProvider(Module owningModule)
    {
        super(NAME, owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {

    }

    @Override
    public void preDeleteStatusFile(User user, PipelineStatusFile sf)
    {
        //TODO
    }
}
