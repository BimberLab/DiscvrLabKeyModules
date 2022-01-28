package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

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
                    SeuratToolParameter.create("emptyDropsLower", "Lower", "Passed to DropletUtils::emptyDrops lower argument", "ldk-integerfield", null, 200),
                    SeuratToolParameter.create("emptyDropsFdrThreshold", "FDR Threshold", "The FDR limit used to filter the results of DropletUtils::emptyDrops", "ldk-numberfield", null, 0.001)
            ), null, null);
        }

        @Override
        public PrepareRawCounts create(PipelineContext ctx)
        {
            return new PrepareRawCounts(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "counts";
    }
}
