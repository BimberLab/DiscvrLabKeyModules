package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Arrays;
import java.util.Collection;

public class CalculatePerforinUCell extends AbstractRiraStep
{
    public CalculatePerforinUCell(PipelineContext ctx, CalculatePerforinUCell.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CalculatePerforinUCell", "Calculate Perforin UCell", "Seurat", "This will calculate two neighbor-smoothed UCells. One using PRF1, and one using PRF1+GZMB.", Arrays.asList(
                    SeuratToolParameter.create("assayName", "Assay Name", "Passed directly to UCell::AddModuleScore_UCell.", "textfield", new JSONObject(){{

                    }}, "RNA"),
                    SeuratToolParameter.create("forceRecalculate", "Force Recalculate", "If checked, the UCell score will always be re-calculated.", "checkbox", new JSONObject(){{

                    }}, false)
            ), null, null);
        }

        @Override
        public CalculatePerforinUCell create(PipelineContext ctx)
        {
            return new CalculatePerforinUCell(ctx, this);
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
