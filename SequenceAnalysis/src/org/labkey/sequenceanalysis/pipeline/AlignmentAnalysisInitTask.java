package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.pipeline.AnalysisStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class AlignmentAnalysisInitTask extends WorkDirectoryTask<AlignmentAnalysisInitTask.Factory>
{
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

        public String getStatusName()
        {
            return "PREPARING FOR ANALYSIS";
        }

        public List<String> getProtocolActionNames()
        {
            List<String> allowableNames = new ArrayList<>();
            for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(AnalysisStep.class))
            {
                allowableNames.add(provider.getLabel());
            }

            return allowableNames;
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            AlignmentAnalysisInitTask task = new AlignmentAnalysisInitTask(this, job);
            return task;
        }

        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new FileType(".bam"));
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        SequenceTaskHelper taskHelper = new SequenceTaskHelper(getJob(), _wd);
        AlignmentAnalysisWorkTask.Helper analysisHelper = new AlignmentAnalysisWorkTask.Helper(taskHelper);
        analysisHelper.cacheAnalysisModels();

        List<AnalysisModel> models = new ArrayList<>(analysisHelper.getAnalysisMap().values());
        List<PipelineStepProvider<AnalysisStep>> providers = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
        if (providers.isEmpty())
        {
            throw new PipelineJobException("No analysis selected, nothing to do");
        }

        getJob().getLogger().info("Preparing for analysis");
        for (PipelineStepProvider<AnalysisStep> provider : providers)
        {
            AnalysisStep step = provider.create(taskHelper);
            step.init(models);
        }

        return new RecordedActionSet();
    }
}
