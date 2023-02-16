package org.labkey.api.sequenceanalysis.pipeline;

/**
 * Created by bimber on 7/26/2017.
 */
public interface PipelineStepCtx<StepType extends PipelineStep>
{
    PipelineStepProvider<StepType> getProvider();

    int getStepIdx();
}
