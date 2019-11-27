package org.labkey.api.sequenceanalysis.pipeline;

public interface AlignmentStepProvider extends PipelineStepProvider
{
    default boolean shouldRunIdxstats()
    {
        return true;
    }
}
