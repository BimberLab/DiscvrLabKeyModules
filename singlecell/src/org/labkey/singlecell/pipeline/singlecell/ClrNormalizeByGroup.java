package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class ClrNormalizeByGroup extends AbstractCellMembraneStep
{
    public ClrNormalizeByGroup(PipelineContext ctx, ClrNormalizeByGroup.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("ClrNormalizeByGroup", "CLR Normalize By Group", "CellMembrane/Seurat", "This will run standard Seurat CLR normalization on the desired assay, subsetting by group.", Arrays.asList(
                    SeuratToolParameter.create("groupingVar", "Group Variable", "The variable on which to group.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, "cDNA_ID"),
                    SeuratToolParameter.create("assayName", "Source Assay", "The source assay", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, "ADT"),
                    SeuratToolParameter.create("targetAssayName", "Target Assay", "The target assay. Will overwrite the source if blank.", "textfield", new JSONObject(){{

                    }}, null),
                    SeuratToolParameter.create("margin", "Margin", "Passing to NormalizeData. Either 1 or 2.", "ldk-integerfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, 1),
                    SeuratToolParameter.create("minCellsPerGroup", "Min Cells Per Group", "Any group with fewer than this many cells will be dropped.", "ldk-integerfield", new JSONObject(){{

                    }}, 20),
                    SeuratToolParameter.create("calculatePerCellUCell", "Calculate Per Feature UCell ", "If checked, ScaleData will only be performed on VariableFeatures, which should dramatically reduce time and memory", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
                    ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public ClrNormalizeByGroup create(PipelineContext ctx)
        {
            return new ClrNormalizeByGroup(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "clrByGroup";
    }
}


