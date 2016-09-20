package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
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
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        public String getStatusName()
        {
            return "PERFORMING ANALYSIS";
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
            AlignmentAnalysisWorkTask task = new AlignmentAnalysisWorkTask(this, job);
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

    private AlignmentAnalysisJob getPipelineJob()
    {
        return (AlignmentAnalysisJob)getJob();
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        _taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);

        List<RecordedAction> actions = new ArrayList<>();

        List<PipelineStepProvider<AnalysisStep>> providers = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
        if (providers.isEmpty())
        {
            throw new PipelineJobException("No analysis selected, nothing to do");
        }

        getJob().getLogger().info("Processing Alignments");
        List<AnalysisStep.Output> outputs = new ArrayList<>();
        Map<String, AnalysisModel> alignmentMap = getAnalysisMap();
        for (File inputBam : getTaskHelper().getJob().getInputFiles())
        {
            AnalysisModel m = alignmentMap.get(inputBam.getName());
            if (m == null)
            {
                throw new PipelineJobException("Unable to find analysis details for file: " + inputBam.getName());
            }

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
                throw new PipelineJobException("Unable to find reference FASTA for file: " + inputBam.getName());
            }

            File outDir = new File(getTaskHelper().getJob().getAnalysisDirectory(), FileUtil.getBaseName(inputBam));
            if (!outDir.exists())
            {
                outDir.mkdirs();
            }

            outputs.addAll(SequenceAnalysisTask.runAnalysesLocal(actions, m, inputBam, refFasta, providers, getTaskHelper(), outDir));
        }

        return new RecordedActionSet(actions);
    }

    private Map<String, AnalysisModel> getAnalysisMap() throws PipelineJobException
    {
        Map<String, AnalysisModel> ret = new HashMap<>();
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

                ret.put(d.getFile().getName(), m);
            }
        }

        return ret;
    }

    public SequenceTaskHelper getTaskHelper()
    {
        return _taskHelper;
    }
}
