package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class CalculateTcrRepertoireStats extends AbstractRDiscvrStep
{
    public CalculateTcrRepertoireStats(PipelineContext ctx, CalculateTcrRepertoireStats.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CalculateTcrRepertoireStats", "Calculate TCR Repertoire Stats", "CellMembrane/tcrdist3", "This will calculate TCR diversity metrics, and save the results in the database.", Arrays.asList(

            ), null, null);
        }

        @Override
        public CalculateTcrRepertoireStats create(PipelineContext ctx)
        {
            return new CalculateTcrRepertoireStats(ctx, this);
        }
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return false;
    }

    @Override
    public String getFileSuffix()
    {
        return "ts";
    }
}
