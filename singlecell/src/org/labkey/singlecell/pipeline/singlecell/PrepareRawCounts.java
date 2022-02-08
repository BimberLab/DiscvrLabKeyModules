package org.labkey.singlecell.pipeline.singlecell;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellRawDataStep;

import java.io.File;
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
                    SeuratToolParameter.create("nExpectedCells", "# Expected Cells", "Only applied if emptyDropsCellRanger is selected. Passed to n.expected.cells argument", "ldk-integerfield", null, false),
                    SeuratToolParameter.create("useCellBender", "Run CellBender", "If checked, cellbender will be run on the raw count matrix (instead of emptyDrops) to remove background/ambient RNA signal", "checkbox", new JSONObject(){{

                    }}, false),
                    SeuratToolParameter.create("useSoupX", "Run SoupX", "If checked, SoupX will be run on the raw count matrix (instead of emptyDrops) to remove background/ambient RNA signal", "checkbox", new JSONObject(){{

                    }}, false)
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

    public static class TestCase extends Assert
    {
        @Test
        public void testStepClass()
        {
            assertEquals(SingleCellRawDataStep.STEP_TYPE, SequencePipelineService.get().getParamNameForStepType(SingleCellRawDataStep.class));
            assertEquals(SingleCellRawDataStep.class, new Provider().getStepClass());
        }
    }
}
