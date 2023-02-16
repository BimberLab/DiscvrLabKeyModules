package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.pipeline.AbstractSequenceTaskFactory;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 8/4/2014.
 */
public class AlignmentAnalysisWorkTask extends WorkDirectoryTask<AlignmentAnalysisWorkTask.Factory>
{
    private SequenceTaskHelper _taskHelper;

    protected AlignmentAnalysisWorkTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(AlignmentAnalysisWorkTask.class);
        }

        @Override
        public String getStatusName()
        {
            return "PERFORMING ANALYSIS";
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
            AlignmentAnalysisWorkTask task = new AlignmentAnalysisWorkTask(this, job);
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

    private AlignmentAnalysisJob getPipelineJob()
    {
        return (AlignmentAnalysisJob)getJob();
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        _taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);

        List<RecordedAction> actions = new ArrayList<>();

        List<PipelineStepCtx<AnalysisStep>> steps = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
        if (steps.isEmpty())
        {
            throw new PipelineJobException("No analysis selected, nothing to do");
        }

        getJob().getLogger().info("Processing Alignments");
        List<AnalysisStep.Output> outputs = new ArrayList<>();
        AnalysisModel m = AnalysisModelImpl.getFromDb(getPipelineJob().getAnalyisId(), getJob().getUser());

        File refFasta = m.getReferenceLibraryFile(getJob().getUser());
        if (refFasta == null)
        {
            TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
            Integer refFastaId = new TableSelector(ti, PageFlowUtil.set("fasta_file")).getObject(m.getLibraryId(), Integer.class);
            if (refFastaId != null)
            {
                ExpData d = ExperimentService.get().getExpData(refFastaId);
                refFasta = d == null ? null : d.getFile();
            }
        }

        if (refFasta == null)
        {
            throw new PipelineJobException("Unable to find reference FASTA for analysis: " + getPipelineJob().getAnalyisId());
        }

        File outDir = new File(getTaskHelper().getJob().getAnalysisDirectory(), FileUtil.getBaseName(m.getAlignmentFileObject()));
        if (!outDir.exists())
        {
            outDir.mkdirs();
        }

        outputs.addAll(SequenceAnalysisTask.runAnalysesLocal(actions, m, m.getAlignmentFileObject(), refFasta, steps, getTaskHelper(), outDir));

        return new RecordedActionSet(actions);
    }

    public SequenceTaskHelper getTaskHelper()
    {
        return _taskHelper;
    }
}
