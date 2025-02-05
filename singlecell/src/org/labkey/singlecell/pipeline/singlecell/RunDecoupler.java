package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunDecoupler extends AbstractCellMembraneStep
{
    public RunDecoupler(PipelineContext ctx, RunDecoupler.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunDecoupler", "Run decoupleR", "decoupleR", "This will run decoupleR to score transcription factor enrichment.", Arrays.asList(

            ), null, null);
        }

        @Override
        public RunDecoupler create(PipelineContext ctx)
        {
            return new RunDecoupler(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "decoupler";
    }
}