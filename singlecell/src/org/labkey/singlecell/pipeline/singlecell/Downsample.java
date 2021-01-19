package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class Downsample extends AbstractOosapStep
{
    public Downsample(PipelineContext ctx, Downsample.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("Downsample", "Downsample Cells", "OOSAP", "This will downsample cells from the input object(s) based on the parameters below.", Arrays.asList(

            ), null, null);
        }


        @Override
        public Downsample create(PipelineContext ctx)
        {
            return new Downsample(ctx, this);
        }
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}
