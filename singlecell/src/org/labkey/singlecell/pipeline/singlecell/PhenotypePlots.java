package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class PhenotypePlots extends AbstractRiraStep
{
    public PhenotypePlots(PipelineContext ctx, PhenotypePlots.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("PhenotypePlots", "Immune Phenotype Plots", "Seurat", "This will generate FeaturePlots for a pre-defined set of immune markers. Any feature not present is skipped.", null, List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public PhenotypePlots create(PipelineContext ctx)
        {
            return new PhenotypePlots(ctx, this);
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
        return "phenotype";
    }
}
