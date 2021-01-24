package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class DoubletFinder extends AbstractCellMembraneStep
{
    public DoubletFinder(PipelineContext ctx, DoubletFinder.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("DoubletFinder", "DoubletFinder", "DoubletFinder", "This will run DoubletFinder to identify putative doublets.", Arrays.asList(
                    ToolParameterDescriptor.create("dropDoublets", "Drop Doublets", "This will run DoubletFinder on the seurat object(s)", "checkbox", new JSONObject(){{

                    }}, false)
            ), null, null);
        }

        @Override
        public DoubletFinder create(PipelineContext ctx)
        {
            return new DoubletFinder(ctx, this);
        }
    }
}
