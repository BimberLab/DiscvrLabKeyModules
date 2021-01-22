package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class RemoveCellCycle extends AbstractOosapStep
{
    public RemoveCellCycle(PipelineContext ctx, RemoveCellCycle.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RemoveCellCycle", "Remove Cell Cycle", "OOSAP", "This will score cells by cell cycle phase and regress out cell cycle.", Arrays.asList(

            ), null, null);
        }


        @Override
        public RemoveCellCycle create(PipelineContext ctx)
        {
            return new RemoveCellCycle(ctx, this);
        }
    }
}

