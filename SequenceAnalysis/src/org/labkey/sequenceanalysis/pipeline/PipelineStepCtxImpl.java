package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;

/**
 * Created by bimber on 7/26/2017.
 */
public class PipelineStepCtxImpl<StepType extends PipelineStep> implements PipelineStepCtx<StepType>
{
    private PipelineStepProvider<StepType> _provider;
    private int _stepIdx = 0;

    public PipelineStepCtxImpl(PipelineStepProvider<StepType> provider, int stepIdx)
    {
        _provider = provider;
        _stepIdx = stepIdx;
    }

    public PipelineStepProvider<StepType> getProvider()
    {
        return _provider;
    }

    public int getStepIdx()
    {
        return _stepIdx;
    }
}
