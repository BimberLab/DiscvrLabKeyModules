package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractSequenceTaskFactory;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.run.util.FastaIndexer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/4/2014.
 */
public class AlignmentAnalysisRemoteWorkTask extends WorkDirectoryTask<AlignmentAnalysisRemoteWorkTask.Factory>
{
    private SequenceTaskHelper _taskHelper;

    protected AlignmentAnalysisRemoteWorkTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(AlignmentAnalysisRemoteWorkTask.class);
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
            AlignmentAnalysisRemoteWorkTask task = new AlignmentAnalysisRemoteWorkTask(this, job);
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

        Map<Integer, File> cachedFiles = getTaskHelper().getSequenceSupport().getAllCachedData();
        getJob().getLogger().debug("total ExpDatas cached: " + cachedFiles.size());
        for (Integer dataId : cachedFiles.keySet())
        {
            getJob().getLogger().debug("file was cached: " + dataId + " / " + cachedFiles.get(dataId).getPath());
        }

        List<RecordedAction> actions = new ArrayList<>();

        List<PipelineStepCtx<AnalysisStep>> steps = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
        if (steps.isEmpty())
        {
            throw new PipelineJobException("No analysis selected, nothing to do");
        }

        getJob().getLogger().info("Processing Alignments");
        Map<String, AnalysisModel> alignmentMap = getAnalysisMap();

        List<AnalysisStep.Output> outputs = new ArrayList<>();
        for (File inputBam : getTaskHelper().getSupport().getInputFiles())
        {
            AnalysisModel m = alignmentMap.get(inputBam.getName());
            if (m == null)
            {
                throw new PipelineJobException("Unable to find analysis details for file: " + inputBam.getName());
            }

            File refFasta = resolveRefFasta(m, inputBam);
            File fai = new File(refFasta.getPath() + ".fai");
            if (!fai.exists())
            {
                getJob().getLogger().info("creating FASTA Index");
                new FastaIndexer(getJob().getLogger()).execute(refFasta);
            }

            Readset rs = getTaskHelper().getSequenceSupport().getCachedReadset(m.getReadset());
            ReferenceGenome genome = getTaskHelper().getSequenceSupport().getCachedGenome(m.getLibraryId());
            outputs.addAll(SequenceAnalysisTask.runAnalysesRemote(actions, rs, inputBam, genome, steps, getTaskHelper()));
        }

        getTaskHelper().getFileManager().deleteIntermediateFiles();
        getTaskHelper().getFileManager().cleanup(actions);

        return new RecordedActionSet(actions);
    }

    //this is an artifact from before reference_libraries, and needed to support one-off custom references:
    private File resolveRefFasta(AnalysisModel m, File inputBam) throws PipelineJobException
    {
        //first try to find FASTA based on reference_library
        Integer refFastaId = m.getReferenceLibrary();
        if (refFastaId != null)
        {
            File refFasta = getTaskHelper().getSequenceSupport().getCachedData(refFastaId);
            if (refFasta != null)
            {
                return refFasta;
            }
        }

        //if null, fall back to the genome
        ReferenceGenome genome = getTaskHelper().getSequenceSupport().getCachedGenome(m.getLibraryId());
        if (genome != null)
        {
            refFastaId = genome.getFastaExpDataId();
        }

        if (refFastaId == null)
        {
            throw new PipelineJobException("Unable to find reference FASTA ID for file: " + inputBam.getName());
        }

        File refFasta = getTaskHelper().getSequenceSupport().getCachedData(refFastaId);
        if (refFasta == null)
        {
            throw new PipelineJobException("Unable to find reference FASTA for file: " + inputBam.getName() + " for id: " + refFastaId);
        }

        return refFasta;
    }

    public SequenceTaskHelper getTaskHelper()
    {
        return _taskHelper;
    }

    private Map<String, AnalysisModel> getAnalysisMap()
    {
        Map<String, AnalysisModel> ret = new HashMap<>();

        for (AnalysisModel m : getTaskHelper().getSequenceSupport().getCachedAnalyses())
        {
            int bamId = m.getAlignmentFile();
            File bam = getTaskHelper().getSequenceSupport().getCachedData(bamId);
            if (bam != null)
            {
                getJob().getLogger().debug("using cached bam: " + bam.getPath());
                ret.put(bam.getName(), m);
            }
            else
            {
                getTaskHelper().getLogger().error("Unable to find BAM for analysis: " + m.getRowId());
            }
        }

        return ret;
    }
}
