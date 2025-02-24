package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class PerformDefaultNimbleAppend extends AbstractRDiscvrStep
{
    public PerformDefaultNimbleAppend(PipelineContext ctx, PerformDefaultNimbleAppend.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("PerformDefaultNimbleAppend", "Default Nimble Append", "RDiscvr", "This uses Rdiscvr to run the default nimble append, adding MHC, KIR, NKG, Viral and Ig data", Arrays.asList(
                    SeuratToolParameter.create("appendMHC", "Append MHC", "If true, MHC data will be appended", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("appendKIR", "Append KIR", "If true, KIR data will be appended", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("appendNKG", "Append NKG2", "If true, NKG2 data will be appended", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("appendIG", "Append Ig", "If true, immunoglobulin data will be appended", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("appendViral", "Append Viral", "If true, viral data will be appended", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
            ), null, null);
        }

        @Override
        public PerformDefaultNimbleAppend create(PipelineContext ctx)
        {
            return new PerformDefaultNimbleAppend(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "nbl";
    }
}

