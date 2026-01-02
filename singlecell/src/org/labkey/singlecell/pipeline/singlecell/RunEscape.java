package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class RunEscape extends AbstractCellMembraneStep
{
    public RunEscape(PipelineContext ctx, Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunEscape", "Escape/ssGSEA", "escape", "Runs escape to perform ssGSEA using Hallmark gene sets.", Arrays.asList(
                    SeuratToolParameter.create("outputAssayBaseName", "Output Assay Basename", "The name of the assay to store results", "textfield", new JSONObject(){{
                        put("allowBank", false);
                    }}, "escape."),
                    SeuratToolParameter.create("escapeMethod", "Escape Method", "Passed directly to escape::runEscape()", "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", false);
                        put("allowBlank", false);
                        put("storeValues", "ssGSEA;GSVA;UCell;AUCell");
                        put("initialValues", "ssGSEA");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, null),
                    SeuratToolParameter.create("performDimRedux", "Perform DimRedux", "If true, the standard seurat PCA/FindClusters/UMAP process will be run on the escape data. This may be most useful when using a customGeneSet or a smaller set of features/pathways", "checkbox", new JSONObject(){{

                    }}, false, null, true),
                    SeuratToolParameter.create("heatmapGroupingVars", "Heatmap Grouping Vars", "Enter one field name per line, which will be used to generate a heatmap of results", "sequenceanalysis-trimmingtextarea", new JSONObject()
                    {{
                        put("allowBlank", true);
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, "ClusterNames_0.2", null, true, true).delimiter(",")
            ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public RunEscape create(PipelineContext ctx)
        {
            return new RunEscape(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "escape";
    }
}