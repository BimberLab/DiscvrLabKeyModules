package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class DoubletFinder extends AbstractOosapStep
{
    public DoubletFinder(PipelineContext ctx, DoubletFinder.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("DoubletFinder", "DoubletFinder", "DoubletFinder", "This will run DoubletFinder to identify putative doublets.", Arrays.asList(

            ), null, null);
        }


        @Override
        public DoubletFinder create(PipelineContext ctx)
        {
            return new DoubletFinder(ctx, this);
        }
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}
