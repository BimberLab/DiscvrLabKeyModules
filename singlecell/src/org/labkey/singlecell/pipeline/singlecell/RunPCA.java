package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunPCA extends AbstractCellMembraneStep
{
    public RunPCA(PipelineContext ctx, RunPCA.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunPCA", "Run PCA", "CellMembrane/Seurat", "This will run standard Seurat PCA-related steps.", Arrays.asList(
                    SeuratToolParameter.create("npcs", "NPCs", "Total Number of PCs to compute and store", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 50)
            ), null, null);
        }


        @Override
        public RunPCA create(PipelineContext ctx)
        {
            return new RunPCA(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "pca";
    }
}

