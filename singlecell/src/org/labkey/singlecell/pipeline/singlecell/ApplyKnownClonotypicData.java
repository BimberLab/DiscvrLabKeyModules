package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.List;

public class ApplyKnownClonotypicData extends AbstractRDiscvrStep
{
    public ApplyKnownClonotypicData(PipelineContext ctx, ApplyKnownClonotypicData.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("ApplyKnownClonotypicData", "Append Known Clonotype/Antigen Data", "RDiscvr", "This will query the clone_responses table and append a column tagging each cell for matching antigens (based on clonotype)", List.of(
                    SeuratToolParameter.create("antigenInclusionList", "Antigen(s) to Include", "Enter antigens, per line. Only stims using these antigens will be used", "sequenceanalysis-trimmingtextarea", new JSONObject()
                    {{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                        put("replaceAllWhitespace", false);
                    }}, null, null, true, true).delimiter(","),
                    SeuratToolParameter.create("antigenExclusionList", "Antigen(s) to Exclude", "Enter antigens, per line. Stims using these antigens will be excluded", "sequenceanalysis-trimmingtextarea", new JSONObject()
                    {{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                        put("replaceAllWhitespace", false);
                    }}, null, null, true, true).delimiter(","),
                    SeuratToolParameter.create("minActivationFrequency", "Minimum Activation Frequency", "If provided, only responses with an activation frequency (of the parent population), will be included", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1.0);
                        put("decimalPrecision", 4);
                    }}, 0.0005),
                    SeuratToolParameter.create("minFractionCloneActivated", "Min Fraction Clone Activated", "If provided, only responses where the fraction of cells harboring that clone were activated will be included", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1.0);
                        put("decimalPrecision", 4);
                    }}, 0.05)
            ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public ApplyKnownClonotypicData create(PipelineContext ctx)
        {
            return new ApplyKnownClonotypicData(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "ctd";
    }
}

