package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunScGate extends AbstractRiraStep
{
    public RunScGate(PipelineContext ctx, RunScGate.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunScGate", "Run scGate", "scGate", "This will run scGate with the default built-in models and create a consensus call.", Arrays.asList(

            ), null, null);
        }

        @Override
        public RunScGate create(PipelineContext ctx)
        {
            return new RunScGate(ctx, this);
        }
    }
}
