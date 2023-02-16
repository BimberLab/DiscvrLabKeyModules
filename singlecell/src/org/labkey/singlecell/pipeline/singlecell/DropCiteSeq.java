package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class DropCiteSeq extends AbstractCellMembraneStep
{
    public DropCiteSeq(PipelineContext ctx, DropCiteSeq.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("DropCiteSeq", "Drop CiteSeq", "CellMembrane", "This will remove the CITE-seq (ADT) assay, if it exists.", List.of(), null, null);
        }

        @Override
        public DropCiteSeq create(PipelineContext ctx)
        {
            return new DropCiteSeq(ctx, this);
        }
    }
}
