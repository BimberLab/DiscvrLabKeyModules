package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class ClassifyTNKByExpression extends AbstractRDiscvrStep
{
    public ClassifyTNKByExpression(PipelineContext ctx, ClassifyTNKByExpression.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("ClassifyTNKByExpression", "Classify T/NK By Expression", "RDiscvr", "Classify T and NK By Expression and TCR clonotype, using best available evidence of ground-truth.", Arrays.asList(
            ), null, null);
        }


        @Override
        public ClassifyTNKByExpression create(PipelineContext ctx)
        {
            return new ClassifyTNKByExpression(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "tnk";
    }
}
