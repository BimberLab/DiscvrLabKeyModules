package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.pipeline.AbstractSequenceTaskFactory;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class AlignmentAnalysisInitTask extends WorkDirectoryTask<AlignmentAnalysisInitTask.Factory>
{
    private SequenceTaskHelper _taskHelper;

    protected AlignmentAnalysisInitTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(AlignmentAnalysisInitTask.class);
        }

        @Override
        public String getStatusName()
        {
            return "PREPARING FOR ANALYSIS";
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            List<String> allowableNames = new ArrayList<>();
            for (PipelineStepProvider<?> provider: SequencePipelineService.get().getProviders(AnalysisStep.class))
            {
                allowableNames.add(provider.getLabel());
            }

            return allowableNames;
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new AlignmentAnalysisInitTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new FileType(".bam"));
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    public SequenceTaskHelper getTaskHelper()
    {
        return _taskHelper;
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        _taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);

        getTaskHelper().cacheExpDatasForParams();

        List<PipelineStepCtx<AnalysisStep>> steps = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
        if (steps.isEmpty())
        {
            throw new PipelineJobException("No analysis selected, nothing to do");
        }

        getJob().getLogger().info("Preparing for analysis");
        for (PipelineStepCtx<AnalysisStep> stepCtx : steps)
        {
            AnalysisStep step = stepCtx.getProvider().create(getTaskHelper());
            step.setStepIdx(stepCtx.getStepIdx());
            step.init(getTaskHelper().getSequenceSupport());
        }

        return new RecordedActionSet();
    }

    private AlignmentAnalysisJob getPipelineJob()
    {
        return (AlignmentAnalysisJob)getJob();
    }
}
