package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class SeuratPreprocessing extends AbstractOosapStep
{
    public SeuratPreprocessing(PipelineContext ctx, SeuratPreprocessing.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("SeuratPreprocessing", "Pre-processing", "OOSAP", "This step reads the raw count matrix/matrices, runs EmptyDrops, and can perform basic filtering.", Arrays.asList(

            ), null, null);
        }


        @Override
        public SeuratPreprocessing create(PipelineContext ctx)
        {
            return new SeuratPreprocessing(ctx, this);
        }
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}
