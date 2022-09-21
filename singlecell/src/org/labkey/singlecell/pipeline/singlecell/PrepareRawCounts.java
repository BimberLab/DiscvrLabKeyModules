package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellRawDataStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class PrepareRawCounts extends AbstractCellMembraneStep implements SingleCellRawDataStep
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
                    SeuratToolParameter.create("emptyDropsLower", "Lower", "Passed to DropletUtils::emptyDrops lower argument", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 200),
                    SeuratToolParameter.create("emptyDropsFdrThreshold", "FDR Threshold", "The FDR limit used to filter the results of DropletUtils::emptyDrops", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("decimalPrecision", 4);
                    }}, 0.001),
                    SeuratToolParameter.create("maxAllowableCells", "Max Cells Allowed", "If more than this many cells are predicted by EmptyDrops, the job will fail", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 20000),
                    SeuratToolParameter.create("minAllowableCells", "Min Cells Allowed", "If fewer than this many cells are predicted by EmptyDrops, the job will fail", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 1500),
                    SeuratToolParameter.create("useEmptyDropsCellRanger", "Use emptyDropsCellRanger", "If checked, this will run emptyDropsCellRanger instead of emptyDrops", "checkbox", null, false),
                    SeuratToolParameter.create("nExpectedCells", "# Expected Cells", "Only applied if emptyDropsCellRanger is selected. Passed to n.expected.cells argument", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, false),
                    SeuratToolParameter.create("useCellBender", "Run CellBender", "If checked, instead of the loupe counts, the system will attempt to find a previously created cellbender corrected count matrix and use this as input", "checkbox", new JSONObject(){{

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
    public void init(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        for (SequenceOutputFile so : inputFiles)
        {
            boolean useCellBender = getProvider().getParameterByName("useCellBender").extractValue(ctx.getJob(), getProvider(), getStepIdx(), Boolean.class, false);
            if (useCellBender)
            {
                // This is the outs dir:
                File source = so.getFile().getParentFile();
                File expected = new File(source, "raw_feature_bc_matrix.cellbender_filtered.h5");
                if (!expected.exists())
                {
                    throw new IllegalArgumentException("Missing cellbender-corrected matrix. You can re-run cellbender on the loupe file to fix this: " + expected.getPath());
                }
            }
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
