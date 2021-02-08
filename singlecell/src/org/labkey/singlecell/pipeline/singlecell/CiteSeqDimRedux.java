package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class CiteSeqDimRedux extends AbstractCellMembraneStep
{
    public CiteSeqDimRedux(PipelineContext ctx, CiteSeqDimRedux.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CiteSeqDimRedux", "CiteSeq DimRedux", "CellMembrane/Seurat", "This will run DimRedux steps on the ADT data.", Arrays.asList(

            ), null, null);
        }

        @Override
        public CiteSeqDimRedux create(PipelineContext ctx)
        {
            return new CiteSeqDimRedux(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "cite-dr";
    }
}
