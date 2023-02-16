package org.labkey.api.sequenceanalysis.run;

import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;

abstract public class AbstractAlignmentPipelineStep<Wrapper extends CommandWrapper> extends AbstractCommandPipelineStep<Wrapper>
{
    private final AlignmentStepProvider _provider;

    public AbstractAlignmentPipelineStep(AlignmentStepProvider provider, PipelineContext ctx, Wrapper wrapper)
    {
        super(provider, ctx, wrapper);
        _provider = provider;
    }

    @Override
    public AlignmentStepProvider getProvider()
    {
        return _provider;
    }
}
