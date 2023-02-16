package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class DietSeurat extends AbstractCellMembraneStep
{
    public DietSeurat(PipelineContext ctx, DietSeurat.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("DietSeurat", "DietSeurat", "CellMembrane", "This will run DietSeurat, dropped reductions to reduce object size.", List.of(), null, null);
        }

        @Override
        public DietSeurat create(PipelineContext ctx)
        {
            return new DietSeurat(ctx, this);
        }
    }
}
