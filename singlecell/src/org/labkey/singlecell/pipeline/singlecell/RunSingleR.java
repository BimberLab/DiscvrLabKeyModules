package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class RunSingleR extends AbstractOosapStep
{
    public RunSingleR(PipelineContext ctx, RunSingleR.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunSingleR", "Run SingleR", "OOSAP/SingleR", "This will run singleR on the input object(s), and save the results in metadata.", Arrays.asList(

            ), null, null);
        }


        @Override
        public RunSingleR create(PipelineContext ctx)
        {
            return new RunSingleR(ctx, this);
        }
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}
