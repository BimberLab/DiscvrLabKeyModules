package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RemoveCellCycle extends AbstractCellMembraneStep
{
    public RemoveCellCycle(PipelineContext ctx, RemoveCellCycle.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RemoveCellCycle", "Remove Cell Cycle", "CellMembrane/Seurat", "This will score cells by cell cycle phase and regress out cell cycle.", Arrays.asList(
                    SeuratToolParameter.create("scaleVariableFeaturesOnly", "ScaleData On Variable Features Only", "If checked, ScaleData will only be performed on VariableFeatures, which should dramatically reduce time and memory", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("blockSize", "Block Size", "This will be passed to block.size in Seurat::ScaleData, which determines the number of features processed as a time. Increasing might increase speed at a memory cost, and descreasing on large datasets might reduce memory at a cost of overall speed.", "ldk-integerfield", new JSONObject(){{

                    }}, null, "block.size", false)
            ), null, null);
        }

        @Override
        public RemoveCellCycle create(PipelineContext ctx)
        {
            return new RemoveCellCycle(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "cc";
    }
}

