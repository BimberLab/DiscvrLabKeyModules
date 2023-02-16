package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class ClearCommands extends AbstractCellMembraneStep
{
    public ClearCommands(PipelineContext ctx, ClearCommands.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("ClearCommands", "ClearCommands", "CellMembrane", "This is a workaround to fix legacy saved objects. It clears the commands slot, which included bloated data in some older objects.", List.of(), null, null);
        }

        @Override
        public ClearCommands create(PipelineContext ctx)
        {
            return new ClearCommands(ctx, this);
        }
    }
}
