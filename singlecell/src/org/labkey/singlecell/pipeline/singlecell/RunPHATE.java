package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunPHATE extends AbstractCellMembraneStep
{
    public RunPHATE(PipelineContext ctx, RunPHATE.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunPHATE", "Run PHATE", "CellMembrane/phateR", "This will run PHATE on the input object.", Arrays.asList(
                SeuratToolParameter.create("phateT", "t", "Passed to the t parameter of phateR::phate().", "ldk-integerfield", null, null)
            ), null, null);
        }


        @Override
        public RunPHATE create(PipelineContext ctx)
        {
            return new RunPHATE(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "phate";
    }
}

