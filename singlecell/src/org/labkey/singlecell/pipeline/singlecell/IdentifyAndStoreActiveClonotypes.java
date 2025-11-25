package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.List;

public class IdentifyAndStoreActiveClonotypes extends AbstractRDiscvrStep
{
    public IdentifyAndStoreActiveClonotypes(PipelineContext ctx, IdentifyAndStoreActiveClonotypes.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("IdentifyAndStoreActiveClonotypes", "Identify And Store Active Clonotypes", "Rdiscvr", "This uses RDiscvr::IdentifyAndStoreActiveClonotypes to predict TCR-triggered T cells and save the results to the database", List.of(), null, null);
        }


        @Override
        public IdentifyAndStoreActiveClonotypes create(PipelineContext ctx)
        {
            return new IdentifyAndStoreActiveClonotypes(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "is";
    }
}

