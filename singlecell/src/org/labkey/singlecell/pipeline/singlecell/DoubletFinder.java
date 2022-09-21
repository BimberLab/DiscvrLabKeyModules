package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
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
                    SeuratToolParameter.create("dropDoublets", "Drop Doublets", "If true, any cells flagged as doublets will be dropped from the seurat object", "checkbox", new JSONObject(){{

                    }}, false)
            ), null, null);
        }

        @Override
        public DoubletFinder create(PipelineContext ctx)
        {
            return new DoubletFinder(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "df";
    }
}
