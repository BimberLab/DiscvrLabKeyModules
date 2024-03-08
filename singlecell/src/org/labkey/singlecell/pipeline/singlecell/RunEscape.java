package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.Collections;

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
                    SeuratToolParameter.create("outputAssayName", "Output Assay Name", "The name of the assay to store results", "textfield", new JSONObject(){{
                        put("allowBank", false);
                    }}, "escape.ssGSEA")
            ), null, null);
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