package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunRiraClassification extends AbstractRiraStep
{
    public RunRiraClassification(PipelineContext ctx, RunRiraClassification.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunRiraClassification", "Run RIRA Classification", "RIRA/Celltypist", "This will run RIRA's Celltypist-based models for course cell type and T/NK cells.", Arrays.asList(
                    SeuratToolParameter.create("maxBatchSize", "Max Cells Per Batch", "If the object has more than this many cells, celltypist will be run in batches", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 500000),
                    SeuratToolParameter.create("retainProbabilityMatrix", "Retain Probability Matrix", "If true, the celltypist probability_matrix with per-class probabilities will be stored in meta.data", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("filterDisallowedClasses", "Filter Disallowed Classes", "If true, then cells will be filtered based on expression of common contaminants, such as RBC or platelet genes ", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("maxAllowedUnknown", "Max Allowed Unknown", "If provided, this step will throw an error is more than this fraction of cells fail the check for disallowed UCell combinations", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 2);
                    }}, 0.1)
            ), null, null);
        }

        @Override
        public RunRiraClassification create(PipelineContext ctx)
        {
            return new RunRiraClassification(ctx, this);
        }
    }
}
