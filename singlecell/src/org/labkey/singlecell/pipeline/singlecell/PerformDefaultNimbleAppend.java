package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

public class PerformDefaultNimbleAppend extends AbstractRDiscvrStep
{
    public PerformDefaultNimbleAppend(PipelineContext ctx, PerformDefaultNimbleAppend.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("PerformDefaultNimbleAppend", "Default Nimble Append", "RDiscvr", "This uses Rdiscvr to run the default nimble append, adding MHC, KIR, NKG, Viral and Ig data", null, null, null);
        }

        @Override
        public PerformDefaultNimbleAppend create(PipelineContext ctx)
        {
            return new PerformDefaultNimbleAppend(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "nbl";
    }
}

