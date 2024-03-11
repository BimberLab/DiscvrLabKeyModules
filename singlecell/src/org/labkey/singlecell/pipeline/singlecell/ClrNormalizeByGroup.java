package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

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
                    SeuratToolParameter.create("calculatePerFeatureUCell", "Calculate Per Feature UCell ", "If checked, UCell will be run over each ADT", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("doAsinhTransform", "Do asinh Transform ", "If checked, count data will be transformed using asinh prior to CLR", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("featureWhitelist", "Inclusion List", "These genes, entered comma-separated or one/line, will be added to the default Seurat::VariableFeatures gene set when running PCA", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("featureExclusionList", "Exclusion List", "These genes, entered comma-separated or one/line, will be excluded from the genes passed to RunPCA (which is otherwise determined by Seurat::VariableFeatures)", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, null).delimiter(",")
                    ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
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


