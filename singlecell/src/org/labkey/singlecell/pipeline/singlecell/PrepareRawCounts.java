package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class PrepareRawCounts extends AbstractCellMembraneStep
{
    public static final String LABEL = "Load Raw Counts";

    public PrepareRawCounts(PipelineContext ctx, PrepareRawCounts.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("PrepareRawCounts", LABEL, "CellMembrane/Seurat", "This step reads the raw count matrix/matrices, and runs EmptyDrops to provide an unfiltered count matrix.", Arrays.asList(

            ), null, null);
        }


        @Override
        public PrepareRawCounts create(PipelineContext ctx)
        {
            return new PrepareRawCounts(ctx, this);
        }
    }

    @Override
    protected String printInputFile(SeuratObjectWrapper so)
    {
        return "'" + so.getFile().getName() + "'";
    }
}
