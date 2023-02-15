package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunCelltypist extends AbstractRiraStep
{
    public RunCelltypist(PipelineContext ctx, RunCelltypist.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunCelltypist", "Run Celltypist (Built-In Model)", "Celltypist", "This will run Celltypist with the Immune_All_Low.pkl model.", Arrays.asList(
                    SeuratToolParameter.create( "modelNames", "Model(s)", "The build-in model(s) to use.", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "Immune_All_Low;Immune_All_High;Human_Lung_Atlas;Healthy_COVID19_PBMC");
                        put("allowBlank", false);
                        put("multiSelect", true);
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, "Immune_All_Low.pkl", null, true, true).delimiter(";"),
                    SeuratToolParameter.create("maxAllowableClasses", "Max Allowable Classes", "Celltypist can assign a cell to many classes, creating extremely long labels. Any cell with more than this number of labels will be set to NA", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 5),
                    SeuratToolParameter.create("minFractionToInclude", "Min Fraction To Include", "Any labels representing fewer than this fraction of the cells will be set to NA", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 3);
                    }}, 0.01),
                    SeuratToolParameter.create( "mode", "Mode", "The build-in model(s) to use.", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "best_match;prob_match");
                        put("allowBlank", false);
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, "prob_match", null, true, true).delimiter(";"),
                    SeuratToolParameter.create("useMajorityVoting", "Majority Voting", "If true, celltypist will be run using --majority-voting", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("retainProbabilityMatrix", "Retain Probability Matrix", "If true, the celltypist probability_matrix with per-class probabilities will be stored in meta.data", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("pThreshold", "pThreshold", "This is passed to the --p-thres argument.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 3);
                    }}, 0.5),
                    SeuratToolParameter.create("minProp", "minProp", "This is passed to the --min-prop argument.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 3);
                    }}, 0),
                    SeuratToolParameter.create("maxBatchSize", "Max Cells Per Batch", "If the object has more than this many cells, celltypist will be run in batches", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 500000)
            ), null, null);
        }

        @Override
        public RunCelltypist create(PipelineContext ctx)
        {
            return new RunCelltypist(ctx, this);
        }
    }
}
