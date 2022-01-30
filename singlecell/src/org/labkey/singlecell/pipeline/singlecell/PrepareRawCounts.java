package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellRawDataStep;

import java.util.Arrays;

public class PrepareRawCounts extends AbstractCellMembraneStep
{
    public static final String LABEL = "Load Raw Counts";

    public PrepareRawCounts(PipelineContext ctx, PrepareRawCounts.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellRawDataStep>
    {
        public Provider()
        {
            super("PrepareRawCounts", LABEL, "CellMembrane/Seurat", "This step reads the raw count matrix/matrices, and runs EmptyDrops to provide an unfiltered count matrix.", Arrays.asList(
                    SeuratToolParameter.create("emptyDropsLower", "Lower", "Passed to DropletUtils::emptyDrops lower argument", "ldk-integerfield", null, 200),
                    SeuratToolParameter.create("emptyDropsFdrThreshold", "FDR Threshold", "The FDR limit used to filter the results of DropletUtils::emptyDrops", "ldk-numberfield", null, 0.001),
                    SeuratToolParameter.create("maxAllowableCells", "Max Cells Allowed", "If more than this many cells are predicted by EmptyDrops, the job will fail", "ldk-integerfield", null, 20000),
                    SeuratToolParameter.create("useEmptyDropsCellRanger", "Use emptyDropsCellRanger", "If checked, this will run emptyDropsCellRanger instead of emptyDrops", "checkbox", null, false),
                    SeuratToolParameter.create("nExpectedCells", "# Expected Cells", "Only applied if emptyDropsCellRanger is selected. Passed to n.expected.cells argument", "ldk-integerfield", null, false)
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
