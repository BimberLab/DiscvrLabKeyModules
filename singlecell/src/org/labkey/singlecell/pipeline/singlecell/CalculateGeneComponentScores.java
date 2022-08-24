package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Arrays;
import java.util.Collection;

public class CalculateGeneComponentScores extends AbstractRiraStep
{
    public CalculateGeneComponentScores(PipelineContext ctx, CalculateGeneComponentScores.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CalculateGeneComponentScores", "Calculate Gene Module Scores", "RIRA", "This will generate UCell scores for a set of pre-defined gene modules", Arrays.asList(
                    SeuratToolParameter.create("savedComponent", "Saved Component(s)", "This is the name of the saved component (from RIRA) to apply", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "Tcell_NaiveToEffector");
                        put("multiSelect", true);
                        put("allowBlank", false);
                        put("joinReturnValue", true);
                        put("delimiter", ";");

                    }}, null, null, true, true).delimiter(";")
            ), null, null);
        }

        @Override
        public CalculateGeneComponentScores create(PipelineContext ctx)
        {
            return new CalculateGeneComponentScores(ctx, this);
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
        return "gc";
    }
}
