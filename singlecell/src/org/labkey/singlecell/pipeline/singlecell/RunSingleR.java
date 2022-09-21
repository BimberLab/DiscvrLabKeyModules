package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunSingleR extends AbstractCellMembraneStep
{
    public RunSingleR(PipelineContext ctx, RunSingleR.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunSingleR", "Run SingleR", "CellMembrane/SingleR", "This will run singleR on the input object(s), and save the results in metadata.", Arrays.asList(
                    SeuratToolParameter.create("showHeatmap", "Generate Heatmaps", "If checked, the SingleR heatmaps will be generated. These can be expensive for large datasets and are often less useful than the DimPlots, so skipping them sometimes makes sense.", "checkbox", new JSONObject(){{

                    }}, false, "showHeatmap", true),
                    SeuratToolParameter.create("nThreads", "# Threads", "If provided, this value will be passed to BiocParallel::MulticoreParam().", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, null)
            ), null, null);
        }


        @Override
        public RunSingleR create(PipelineContext ctx)
        {
            return new RunSingleR(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "singler";
    }
}
