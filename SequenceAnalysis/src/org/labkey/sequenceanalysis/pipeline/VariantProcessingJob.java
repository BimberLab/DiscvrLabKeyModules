package org.labkey.sequenceanalysis.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
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
import org.labkey.sequenceanalysis.util.ScatterGatherUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VariantProcessingJob extends SequenceOutputHandlerJob
{
    private ScatterGatherUtils.ScatterGatherMethod _scatterGatherMethod = ScatterGatherUtils.ScatterGatherMethod.none;
    File _dictFile = null;
    Map<String, File> _finalVCFs = new HashMap<>();
    private transient LinkedHashMap<String, List<Interval>> _jobToIntervalMap;

    private String _intervalSetName = null;

    // Default constructor for serialization
    protected VariantProcessingJob()
    {
    }

    protected VariantProcessingJob(VariantProcessingJob parentJob, String intervalSetName) throws IOException
    {
        super(parentJob, getChildJobName(parentJob, intervalSetName), intervalSetName);
        _scatterGatherMethod = parentJob._scatterGatherMethod;
        _dictFile = parentJob._dictFile;
        _jobToIntervalMap = parentJob._jobToIntervalMap;

        _intervalSetName = intervalSetName;
    }

    public VariantProcessingJob(Container c, User user, @Nullable String jobName, PipeRoot pipeRoot, SequenceOutputHandler handler, List<SequenceOutputFile> files, JSONObject jsonParams, ScatterGatherUtils.ScatterGatherMethod scatterGatherMethod) throws IOException, PipelineJobException
    {
        super(c, user, jobName, pipeRoot, handler, files, jsonParams);
        _scatterGatherMethod = scatterGatherMethod;

        if (isScatterJob())
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

            _jobToIntervalMap = establishIntervals();
            writeJobToIntervalMap(_jobToIntervalMap);
        }
    }

    private LinkedHashMap<String, List<Interval>> establishIntervals()
    {
        LinkedHashMap<String, List<Interval>> ret;
        SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(_dictFile.toPath());
        if (_scatterGatherMethod == ScatterGatherUtils.ScatterGatherMethod.contig)
        {
            ret = new LinkedHashMap<>();
            for (SAMSequenceRecord rec : dict.getSequences())
            {
                ret.put(rec.getSequenceName(), Collections.singletonList(new Interval(rec.getSequenceName(), 1, rec.getSequenceLength())));
            }
        }
        else if (_scatterGatherMethod == ScatterGatherUtils.ScatterGatherMethod.chunked)
        {
            int basesPerJob = getParameterJson().getInt("scatterGather.basesPerJob");
            boolean allowSplitChromosomes = getParameterJson().optBoolean("scatterGather.allowSplitChromosomes", true);
            getLogger().info("Creating jobs with target bp size: " + basesPerJob + " mbp.  allow splitting configs: " + allowSplitChromosomes);

            basesPerJob = basesPerJob * 1000000;
            ret = ScatterGatherUtils.divideGenome(dict, basesPerJob, allowSplitChromosomes);

        }
        else if (_scatterGatherMethod == ScatterGatherUtils.ScatterGatherMethod.fixedJobs)
        {
            long totalSize = dict.getReferenceLength();
            int numJobs = getParameterJson().getInt("scatterGather.totalJobs");
            int jobSize = (int)Math.ceil(totalSize / (double)numJobs);
            getLogger().info("Creating " + numJobs + " jobs with approximate size: " + jobSize + " bp.  estimated total jobs: ");
            ret = ScatterGatherUtils.divideGenome(dict, jobSize, true);
        }
        else
        {
            throw new IllegalArgumentException("Unknown scatter type: " + _scatterGatherMethod.name());
        }

        return ret;
    }

    public boolean isScatterJob()
    {
        return _scatterGatherMethod != ScatterGatherUtils.ScatterGatherMethod.none;
    }

    @JsonIgnore
    public List<Interval> getIntervalsForTask()
    {
        Map<String, List<Interval>> allIntervals = getJobToIntervalMap();
        return _intervalSetName == null ? null : allIntervals.get(_intervalSetName);
    }

    public String getIntervalSetName()
    {
        return _intervalSetName;
    }

    public void setIntervalSetName(String intervalSetName)
    {
        _intervalSetName = intervalSetName;
    }

    @JsonIgnore
    private SAMSequenceDictionary getDictionary()
    {
        if (_dictFile == null)
        {
            throw new IllegalStateException("Dictionary file was null");
        }

        return SAMSequenceDictionaryExtractor.extractDictionary(_dictFile.toPath());
    }

    public File getDictFile()
    {
        return _dictFile;
    }

    public void setDictFile(File dictFile)
    {
        _dictFile = dictFile;
    }

    @Override
    public List<PipelineJob> createSplitJobs()
    {
        if (isScatterJob())
        {
            ArrayList<PipelineJob> jobs = new ArrayList<>();
            Map<String, List<Interval>> intervalMap = getJobToIntervalMap();
            for (String name : intervalMap.keySet())
            {
                jobs.add(createSingleContigJob(name));
            }

            return Collections.unmodifiableList(jobs);
        }

        return super.createSplitJobs();
    }

    private File getJobToIntervalFile()
    {
        return isSplitJob() ? getDataDirectory().getParentFile() : getDataDirectory();
    }

    private void writeJobToIntervalMap(Map<String, List<Interval>> jobToIntervalMap) throws IOException
    {
        File out = getJobToIntervalFile();
        try (OutputStream output = IOUtil.maybeBufferOutputStream(IOUtil.openFileForWriting(out)))
        {
            getLogger().info("writing JobToInterval map to file: " + jobToIntervalMap.size());
            ObjectMapper objectMapper = createObjectMapper();
            objectMapper.writeValue(output, jobToIntervalMap);
        }
    }

    @JsonIgnore
    public Map<String, List<Interval>> getJobToIntervalMap()
    {
        if (_jobToIntervalMap == null)
        {
            File json = getJobToIntervalFile();
            try (InputStream is = IOUtil.maybeBufferInputStream(IOUtil.openFileForReading(json)))
            {
                ObjectMapper objectMapper = createObjectMapper();
                _jobToIntervalMap = objectMapper.readValue(is, new TypeReference<LinkedHashMap<String, List<Interval>>>(){});

            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }

        }

        return _jobToIntervalMap;
    }
    private static String getChildJobName(SequenceJob parentJob, String contig)
    {
        return parentJob.getJobName() + "-" + contig;
    }

    private PipelineJob createSingleContigJob(String jobNameSuffix)
    {
        try
        {
            VariantProcessingJob childJob = new VariantProcessingJob(this, jobNameSuffix);

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
        return !isSplitJob() && isScatterJob();
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

    public ScatterGatherUtils.ScatterGatherMethod getScatterGatherMethod()
    {
        return _scatterGatherMethod;
    }

    public void setScatterGatherMethod(ScatterGatherUtils.ScatterGatherMethod scatterGatherMethod)
    {
        _scatterGatherMethod = scatterGatherMethod;
    }

    public static class TestCase extends Assert
    {
        private static final Logger _log = Logger.getLogger(SequenceAlignmentTask.TestCase.class);

        @Test
        public void serializeTest() throws Exception
        {
            VariantProcessingJob job1 = new VariantProcessingJob();
            job1._intervalSetName = "chr1";
            job1._scatterGatherMethod = ScatterGatherUtils.ScatterGatherMethod.chunked;

            File tmp = new File(System.getProperty("java.io.tmpdir"));
            File xml = new File(tmp, "variantProcessingJob.txt");

            ObjectMapper objectMapper = PipelineJob.createObjectMapper();
            objectMapper.writeValue(xml, job1);

            VariantProcessingJob job2 = objectMapper.readValue(xml, VariantProcessingJob.class);
            assertEquals(job1.getIntervalSetName(), job2.getIntervalSetName());
            assertEquals(job1.getScatterGatherMethod(), job2.getScatterGatherMethod());

            xml.delete();
        }
    }
}
