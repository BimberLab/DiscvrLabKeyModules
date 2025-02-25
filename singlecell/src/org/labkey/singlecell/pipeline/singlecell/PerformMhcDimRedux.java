package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class PerformMhcDimRedux extends AbstractRDiscvrStep
{
    public PerformMhcDimRedux(PipelineContext ctx, PerformMhcDimRedux.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("PerformMhcDimRedux", "Perform MHC DimRedux", "RDiscvr", "This will perform dimensionality reduction based on MHC data", Arrays.asList(

            ), null, null);
        }

        @Override
        public PerformMhcDimRedux create(PipelineContext ctx)
        {
            return new PerformMhcDimRedux(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "mhc";
    }
}

