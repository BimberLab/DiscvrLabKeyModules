package org.labkey.api.sequenceanalysis.pipeline;

public interface AlignmentStepProvider<StepType extends PipelineStep> extends PipelineStepProvider<StepType>
{
    default boolean shouldRunIdxstats()
    {
        return true;
    }

    boolean supportsMergeUnaligned();
}
