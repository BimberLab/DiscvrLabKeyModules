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
                    SeuratToolParameter.create("convertAmbiguousToNA", "Convert Ambiguous To NA", "If true, any values for majority_voting with commas (indicating they are ambiguous) will be converted to NA", "checkbox", new JSONObject()
                    {{
                        put("checked", false);
                    }}, false),
                    SeuratToolParameter.create("columnPrefix", "Column Prefix", "If provided, this string will be added to the beginning of all output column names.", "textfield", new JSONObject()
                    {{

                    }}, null),
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
                    }}, 0)
            ), null, null);
        }

        @Override
        public RunCelltypist create(PipelineContext ctx)
        {
            return new RunCelltypist(ctx, this);
        }
    }
}
