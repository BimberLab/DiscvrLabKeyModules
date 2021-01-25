package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class SplitSeurat extends AbstractCellMembraneStep
{
    public SplitSeurat(PipelineContext ctx, SplitSeurat.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("SplitSeurat", "Split Seurat Objects", "OOSAP", "This will split each input seurat object into multiple objects.", Arrays.asList(
                    SeuratToolParameter.create("splitField", "Field Name", "This field will be used to split the seurat object. For each unique value of this field, cells will be subset and a new seurat object created. Any cells lacking a value in this field will be discarded.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null)
            ), null, null);
        }

        @Override
        public SplitSeurat create(PipelineContext ctx)
        {
            return new SplitSeurat(ctx, this);
        }
    }
}
