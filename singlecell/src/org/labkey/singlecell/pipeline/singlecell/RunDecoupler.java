package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunDecoupler extends AbstractCellMembraneStep
{
    public RunDecoupler(PipelineContext ctx, RunDecoupler.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunDecoupler", "Run decoupleR", "decoupleR", "This will run decoupleR to score transcription factor enrichment.", Arrays.asList(
                    SeuratToolParameter.create("heatmapGroupingVars", "Heatmap Grouping Vars", "Enter one field name per line, which will be used to generate a heatmap of results", "sequenceanalysis-trimmingtextarea", new JSONObject()
                    {{
                        put("allowBlank", true);
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, "ClusterNames_0.2", null, true, true).delimiter(",")
            ), null, null);
        }

        @Override
        public RunDecoupler create(PipelineContext ctx)
        {
            return new RunDecoupler(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "decoupler";
    }
}