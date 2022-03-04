package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunCelltypist extends AbstractRiraStep
{
    public RunCelltypist(PipelineContext ctx, RunCelltypist.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunCelltypist", "Run Celltypist (Built-In Model)", "Celltypist", "This will run Celltypist with the Immune_All_Low.pkl model.", Arrays.asList(

            ), null, null);
        }

        @Override
        public RunCelltypist create(PipelineContext ctx)
        {
            return new RunCelltypist(ctx, this);
        }
    }
}
