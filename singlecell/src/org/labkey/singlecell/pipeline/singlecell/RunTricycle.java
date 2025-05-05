package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunTricycle extends AbstractCellMembraneStep
{
    public RunTricycle(PipelineContext ctx, RunTricycle.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunTricycle", "Run Tricycle", "CellMembrane/Tricycle", "This will run tricycle on the input object(s) to score cell cycle, and save the results in metadata.", Arrays.asList(

            ), null, null);
        }

        @Override
        public RunTricycle create(PipelineContext ctx)
        {
            return new RunTricycle(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "tricycle";
    }
}
