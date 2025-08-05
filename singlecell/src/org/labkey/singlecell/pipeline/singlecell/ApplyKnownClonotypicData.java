package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.List;

public class ApplyKnownClonotypicData extends AbstractRDiscvrStep
{
    public ApplyKnownClonotypicData(PipelineContext ctx, ApplyKnownClonotypicData.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("ApplyKnownClonotypicData", "Append Known Clonotype/Antigen Data", "RDiscvr", "This will query the clone_responses table and append a column tagging each cell for matching antigens (based on clonotype)", List.of(

            ), null, null);
        }


        @Override
        public ApplyKnownClonotypicData create(PipelineContext ctx)
        {
            return new ApplyKnownClonotypicData(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "ctd";
    }
}

