package org.labkey.sequenceanalysis.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VariantProcessingJob extends SequenceOutputHandlerJob
{
    private boolean _doScatterByContig = false;
    File _dictFile = null;
    Map<String, File> _finalVCFs = new HashMap<>();

    private String _contigForTask = null;

    private List<String> _allContigs = null;

    // Default constructor for serialization
    protected VariantProcessingJob()
    {
    }

    protected VariantProcessingJob(VariantProcessingJob parentJob, String contig) throws IOException
    {
        super(parentJob, getChildJobName(parentJob, contig), contig);
        _doScatterByContig = parentJob._doScatterByContig;
        _dictFile = parentJob._dictFile;
        _contigForTask = contig;
        _allContigs = parentJob._allContigs;
    }

    public VariantProcessingJob(Container c, User user, @Nullable String jobName, PipeRoot pipeRoot, SequenceOutputHandler handler, List<SequenceOutputFile> files, JSONObject jsonParams, boolean doScatterByContig) throws IOException, PipelineJobException
    {
        super(c, user, jobName, pipeRoot, handler, files, jsonParams);
        _doScatterByContig = doScatterByContig;

        if (_doScatterByContig)
        {
            Set<Integer> genomeIds = new HashSet<>();
            for (SequenceOutputFile so : files)
            {
                genomeIds.add(so.getLibrary_id());
            }

            if (genomeIds.size() != 1)
            {
                throw new PipelineJobException("Expected a single genome for all inputs.  Was: " + genomeIds.size());
            }

            ReferenceGenome genome = SequenceAnalysisService.get().getReferenceGenome(genomeIds.iterator().next(), user);
            _dictFile = genome.getSequenceDictionary();

        }
    }

    public boolean isDoScatterByContig()
    {
        return _doScatterByContig;
    }

    public String getContigForTask()
    {
        return _contigForTask;
    }

    public void setContigForTask(String contigForTask)
    {
        _contigForTask = contigForTask;
    }

    @JsonIgnore
    public List<String> getAllContigs()
    {
        if (_allContigs == null)
        {
            if (_dictFile == null)
            {
                throw new IllegalStateException("Dictionary file was null");
            }

            _allContigs = new ArrayList<>();
            SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(_dictFile.toPath());
            dict.getSequences().forEach(x -> _allContigs.add(x.getSequenceName()));
        }

        return _allContigs;
    }

    public File getDictFile()
    {
        return _dictFile;
    }

    public void setDictFile(File dictFile)
    {
        _dictFile = dictFile;
    }

    public void setDoScatterByContig(boolean doScatterByContig)
    {
        _doScatterByContig = doScatterByContig;
    }

    @Override
    public List<PipelineJob> createSplitJobs()
    {
        if (_doScatterByContig)
        {
            ArrayList<PipelineJob> jobs = new ArrayList<>();
            for (String contig : getAllContigs())
            {
                jobs.add(createSingleContigJob(contig));
            }

            return Collections.unmodifiableList(jobs);
        }

        return super.createSplitJobs();
    }

    private static String getChildJobName(SequenceJob parentJob, String contig)
    {
        return parentJob.getJobName() + "-" + contig;
    }

    private PipelineJob createSingleContigJob(String contig)
    {
        try
        {
            VariantProcessingJob childJob = new VariantProcessingJob(this, contig);
            childJob.setContigForTask(contig);

            return childJob;
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean isSplittable()
    {
        return !isSplitJob() && _doScatterByContig;
    }

    @Override
    public void mergeSplitJob(PipelineJob job)
    {
        super.mergeSplitJob(job);

        if (job instanceof VariantProcessingJob)
        {
            VariantProcessingJob childJob = (VariantProcessingJob)job;
            getLogger().debug("Merging child job VCFs.  total: " + childJob.getFinalVCFs().size());
            _finalVCFs.putAll(childJob.getFinalVCFs());
        }
    }

    public Map<String, File> getFinalVCFs()
    {
        return _finalVCFs;
    }

    public void setFinalVCFs(Map<String, File> finalVCFs)
    {
        _finalVCFs = finalVCFs;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return  PipelineJobService.get().getTaskPipeline(new TaskId(VariantProcessingJob.class));
    }

}
