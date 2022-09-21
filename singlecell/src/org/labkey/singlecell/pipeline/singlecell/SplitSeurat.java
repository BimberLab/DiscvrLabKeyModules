package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
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
            super("SplitSeurat", "Split Seurat Objects", "CellMembrane/Seurat", "This will split each input seurat object into multiple objects.", Arrays.asList(
                    SeuratToolParameter.create("splitField", "Field Name", "This field will be used to split the seurat object. For each unique value of this field, cells will be subset and a new seurat object created. Any cells lacking a value in this field will be discarded.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null),
                    SeuratToolParameter.create("minCellsToKeep", "Min Cells To Keep", "If provided, any subset with fewer than this many cells will be discarded. Use zero to keep all. Note: if the value is less than 1 it will be interpreted as a fraction of the total input cells", "ldk-numberfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("decimalPrecision", 3);
                    }}, 0.05)
            ), null, null);
        }

        @Override
        public SplitSeurat create(PipelineContext ctx)
        {
            return new SplitSeurat(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "split";
    }
}
