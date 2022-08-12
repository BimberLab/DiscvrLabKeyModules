package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
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
            super("CalculateUCellScores", "Calculate UCell Scores", "Seurat", "This will generate UCell scores for a set of pre-defined gene modules", null, null, null);
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
    public boolean createsSeuratObjects()
    {
        return false;
    }

    @Override
    public String getFileSuffix()
    {
        return "ucell";
    }
}