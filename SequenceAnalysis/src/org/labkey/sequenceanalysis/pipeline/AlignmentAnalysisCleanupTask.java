package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.pipeline.AbstractSequenceTaskFactory;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class AlignmentAnalysisCleanupTask extends WorkDirectoryTask<AlignmentAnalysisCleanupTask.Factory>
{
    protected AlignmentAnalysisCleanupTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(AlignmentAnalysisCleanupTask.class);
        }

        @Override
        public String getStatusName()
        {
            return "FINAL ANALYSIS STEPS";
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            List<String> allowableNames = new ArrayList<>();
            for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(AnalysisStep.class))
            {
                allowableNames.add(provider.getLabel());
            }

            return allowableNames;
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            AlignmentAnalysisCleanupTask task = new AlignmentAnalysisCleanupTask(this, job);
            return task;
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

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        SequenceTaskHelper taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);

        taskHelper.getFileManager().createSequenceOutputRecords(getPipelineJob().getAnalyisId());

        return new RecordedActionSet();
    }

    private AlignmentAnalysisJob getPipelineJob()
    {
        return (AlignmentAnalysisJob)getJob();
    }
}
