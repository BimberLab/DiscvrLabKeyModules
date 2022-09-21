package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class AvgExpression extends AbstractCellMembraneStep
{
    public AvgExpression(PipelineContext ctx, AvgExpression.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("AvgExpression", "Avg. Expression", "Seurat", "This will run AverageExpression on the raw counts, producing a matrix with the average per group. This matrix will have a row labeled TotalCells appended, which is the total cells per group.", Arrays.asList(
                    SeuratToolParameter.create("groupField", "Grouping Field", "This field will be used to group cells of the seurat object. For each unique value of this field, count averages will be computed and saved into a matrix with one column per group. Any cells lacking a value in this field will be discarded.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null)
            ), null, null);
        }

        @Override
        public AvgExpression create(PipelineContext ctx)
        {
            return new AvgExpression(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "avg";
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return false;
    }
}

