package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class FilterDisallowedClasses extends AbstractRiraStep
{
    public FilterDisallowedClasses(PipelineContext ctx, FilterDisallowedClasses.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("FilterDisallowedClasses", "Filter Disallowed Classes", "RIRA/Seurat", "This will run FilterDisallowedClasses to flag any cells with conflicting gene expression profiles based on expected cell types.", Arrays.asList(
                    SeuratToolParameter.create("sourceField", "Source Field", "The name of the field with the base cell type call", "textfield", new JSONObject(){{

                    }}, "RIRA_Immune_v1.majority_voting"),
                    SeuratToolParameter.create("outputFieldName", "Output Field", "The name of the field to store the results", "textfield", new JSONObject(){{

                    }}, null),
                    SeuratToolParameter.create("ucellCutoff", "UCell Cutoff", "Any cells expressing the disallowed UCell above this value will be flagged", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("decimalPrecision", 2);
                    }}, 0.2),
                    SeuratToolParameter.create("dropFilteredCells", "Drop Filtered Cells", "If checked, filtered cells will be dropped", "checkbox", new JSONObject(){{

                    }}, false),
                    SeuratToolParameter.create("updateInputColumn", "Mark Filtered Cells As Contaminant", "If checked, the source field for filter cells will be updated to Contaminant", "checkbox", new JSONObject(){{

                    }}, false)
                    ), null, null);
        }

        @Override
        public FilterDisallowedClasses create(PipelineContext ctx)
        {
            return new FilterDisallowedClasses(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "fc";
    }
}


