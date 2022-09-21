package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Arrays;
import java.util.Collection;

public class IntegrateData extends AbstractCellMembraneStep
{
    public IntegrateData(PipelineContext ctx, IntegrateData.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("IntegrateData", "Seurat IntegrateData", "Seurat", "This will perform IntegrateData integration on the seurat object", Arrays.asList(
                    SeuratToolParameter.create("splitField", "Split Field", "This field will be used to split the object prior to integration.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, "SubjectId"),
                    SeuratToolParameter.create("nVariableFeatures", "# Variable Features", "Controls the number of variable features that will be used.", "ldk-integerfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("minValue", 0);
                    }}, 3000),
                    SeuratToolParameter.create("nIntegrationFeatures", "# Integration Features", "Controls the number of features that will be used for integration.", "ldk-integerfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("minValue", 0);
                    }}, 3000),
                    SeuratToolParameter.create("integrationFeaturesInclusionList", "Genes to Add to Integration", "These genes, entered comma-separated or one/line, will be added to the default Seurat::VariableFeatures gene set when running PCA", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("integrationFeaturesExclusionList", "Genes to Exclude From Integration", "These genes, entered comma-separated or one/line, will be excluded from the genes passed to RunPCA (which is otherwise determined by Seurat::VariableFeatures)", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("kWeight", "k.weight", "This will be passed to k.weight on Seurat::IntegrateData", "ldk-integerfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, 20, "k.weight", true),
                    SeuratToolParameter.create("dimsToUse", "IntegrateData Dims", "This will be passed to dims on Seurat::IntegrateData", "ldk-integerfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, 20)
            ), null, null);
        }

        @Override
        public IntegrateData create(PipelineContext ctx)
        {
            return new IntegrateData(ctx, this);
        }
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("Seurat", "patchwork");
    }

    @Override
    public String getFileSuffix()
    {
        return "integrated";
    }
}
