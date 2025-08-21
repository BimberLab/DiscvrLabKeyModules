package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.List;

public class PredictTcellActivation extends AbstractRDiscvrStep
{
    public PredictTcellActivation(PipelineContext ctx, PredictTcellActivation.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("PredictTcellActivation", "Predict T cell Activation", "RIRA", "This uses RIRA::PredictTcellActivation to predict TCR-triggered T cells", List.of(), null, null);
        }


        @Override
        public PredictTcellActivation create(PipelineContext ctx)
        {
            return new PredictTcellActivation(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "tca";
    }
}

