package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Arrays;
import java.util.Collection;

public class CalculateUCellScores extends AbstractRiraStep
{
    public CalculateUCellScores(PipelineContext ctx, CalculateUCellScores.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CalculateUCellScores", "Calculate UCell Scores", "Seurat", "This will generate UCell scores for a set of pre-defined gene modules", Arrays.asList(
                    SeuratToolParameter.create("storeRanks", "Store Ranks", "Passed directly to UCell::AddModuleScore_UCell.", "checkbox", new JSONObject(){{

                    }}, false),
                    SeuratToolParameter.create("assayName", "Assay Name", "Passed directly to UCell::AddModuleScore_UCell.", "textfield", new JSONObject(){{

                    }}, "RNA")
            ), null, null);
        }

        @Override
        public CalculateUCellScores create(PipelineContext ctx)
        {
            return new CalculateUCellScores(ctx, this);
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
        return "ucell";
    }
}
