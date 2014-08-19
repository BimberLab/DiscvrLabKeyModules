package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.api.pipeline.AnalysisStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class AlignmentAnalysisCleanupTask extends WorkDirectoryTask<AlignmentAnalysisCleanupTask.Factory>
{
    private SequenceTaskHelper _taskHelper;

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

        public String getStatusName()
        {
            return "FINAL ANALYSIS STEPS";
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
            AlignmentAnalysisCleanupTask task = new AlignmentAnalysisCleanupTask(this, job);
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
        _taskHelper = new SequenceTaskHelper(getJob(), _wd);

        
        return new RecordedActionSet();
    }    
}
