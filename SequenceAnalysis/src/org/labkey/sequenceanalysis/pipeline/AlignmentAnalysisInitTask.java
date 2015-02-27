package org.labkey.sequenceanalysis.pipeline;

import org.json.JSONObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.FileType;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;

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

    public SequenceTaskHelper getTaskHelper()
    {
        return _taskHelper;
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        _taskHelper = new SequenceTaskHelper(getJob(), _wd);
        List<AnalysisModel> models = cacheAnalysisModels();

        List<PipelineStepProvider<AnalysisStep>> providers = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
        if (providers.isEmpty())
        {
            throw new PipelineJobException("No analysis selected, nothing to do");
        }

        getJob().getLogger().info("Preparing for analysis");
        for (PipelineStepProvider<AnalysisStep> provider : providers)
        {
            AnalysisStep step = provider.create(getTaskHelper());
            step.init(models);
        }

        return new RecordedActionSet();
    }

    public List<AnalysisModel> cacheAnalysisModels() throws PipelineJobException
    {
        List<AnalysisModel> ret = new ArrayList<>();
        for (String key : getTaskHelper().getJob().getParameters().keySet())
        {
            if (key.startsWith("sample_"))
            {
                JSONObject o = new JSONObject(getTaskHelper().getJob().getParameters().get(key));
                Integer analysisId = o.getInt("analysisid");
                AnalysisModel m = AnalysisModelImpl.getFromDb(analysisId, getTaskHelper().getJob().getUser());
                ExpData d = m.getAlignmentData();
                if (d == null)
                {
                    getTaskHelper().getLogger().error("Analysis lacks an alignment file: " + m.getRowId());
                    continue;
                }

                ret.add(m);
            }
        }

        getTaskHelper().getLogger().debug("caching " + ret.size() + " analyses for use on remote server");
        for (AnalysisModel m : ret)
        {
            getTaskHelper().getSequenceSupport().cacheExpData(m.getAlignmentData());
            getTaskHelper().getSequenceSupport().cacheExpData(m.getReferenceLibraryData());
            ((SequenceAnalysisJob)getTaskHelper().getSequenceSupport()).cacheAnalysis(m);

            SequenceReadsetImpl rs = SequenceAnalysisServiceImpl.get().getReadset(m.getReadset(), getJob().getUser());
            ((SequenceAnalysisJob)getTaskHelper().getSequenceSupport()).cacheReadset(rs);

            ReferenceGenome rg = ReferenceGenomeImpl.getForId(m.getLibraryId(), getJob().getUser());
            ((SequenceAnalysisJob)getTaskHelper().getSequenceSupport()).cacheGenome(rg);
            (getTaskHelper().getSequenceSupport()).cacheExpData(ExperimentService.get().getExpData(rg.getFastaExpDataId()));
        }

        return ret;
    }
}
