package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class NormalizeAndScale extends AbstractCellMembraneStep
{
    public NormalizeAndScale(PipelineContext ctx, NormalizeAndScale.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("NormalizeAndScale", "Normalize/Scale", "CellMembrane/Seurat", "This will run standard Seurat processing steps to normalize and scale the data.", Arrays.asList(
                    SeuratToolParameter.create("variableFeatureSelectionMethod", "Variable Feature Selection Method", "The value, passed directly to Seurat's FindVariableFeatures, variableFeatureSelectionMethod", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "vst");
                        put("initialValues", "vst");
                        put("allowBlank", false);
                    }}, "vst"),
                    SeuratToolParameter.create("variableGenesWhitelist", "Genes to Add to VariableFeatures", "These genes, entered comma-separated or one/line, will be added to the default Seurat::VariableFeatures gene set when running PCA", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("variableGenesBlacklist", "Genes to Exclude From VariableFeatures", "These genes, entered comma-separated or one/line, will be excluded from the genes passed to RunPCA (which is otherwise determined by Seurat::VariableFeatures)", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("featuresToRegress", "Features to Regress", "These features, entered comma-separated or one/line, will be passed to Seurat::ScaleData vars.to.regress", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("scaleVariableFeaturesOnly", "ScaleData On Variable Features Only", "If checked, ScaleData will only be performed on VariableFeatures, which should dramatically reduce time and memory", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("blockSize", "Block Size", "This will be passed to block.size in Seurat::ScaleData, which determines the number of features processed as a time. Increasing might increase speed at a memory cost, and decreasing on large datasets might reduce memory at a cost of overall speed.", "ldk-integerfield", new JSONObject(){{

                    }}, null, "block.size", false),
                    SeuratToolParameter.create("useSCTransform", "Use SCTransform", "If checked, SCTransform will be used instead of the default NormalizeData -> ScaleData steps", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    SeuratToolParameter.create("nVariableFeatures", "# Variable Features", "Controls the number of variable features that will be used. This only applies to the standard NormalizeData/ScaleData pipeline, not SCTransform", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, null),
                    getSeuratThreadsParam()
                    ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public NormalizeAndScale create(PipelineContext ctx)
        {
            return new NormalizeAndScale(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "norm";
    }
}


