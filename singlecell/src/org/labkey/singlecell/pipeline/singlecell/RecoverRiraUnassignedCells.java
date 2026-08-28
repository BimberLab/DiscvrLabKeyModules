package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RecoverRiraUnassignedCells extends AbstractRiraStep
{
    public RecoverRiraUnassignedCells(PipelineContext ctx, RecoverRiraUnassignedCells.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RecoverRiraUnassignedCells", "Recover RIRA Unassigned Cells", "RIRA/Celltypist", "This will run RIRA's Celltypist-based models for course cell type and T/NK cells.", Arrays.asList(
                    SeuratToolParameter.create("groupField", "Group Field", "The field on which to group", "textfield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, "ClusterNames_0.2"),
                    SeuratToolParameter.create("minClusterProp", "Min Cluster Proportion", "If at least this proportion of the group is one class, unassigned cells will be assigned as this class", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 2);
                    }}, 0.6)
            ), null, null);
        }

        @Override
        public RecoverRiraUnassignedCells create(PipelineContext ctx)
        {
            return new RecoverRiraUnassignedCells(ctx, this);
        }
    }
}
