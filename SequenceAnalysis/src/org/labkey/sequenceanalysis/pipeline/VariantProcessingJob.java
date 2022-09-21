package org.labkey.sequenceanalysis.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.util.ScatterGatherUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VariantProcessingJob extends SequenceOutputHandlerJob
{
    private VariantProcessingStep.ScatterGatherMethod _scatterGatherMethod = VariantProcessingStep.ScatterGatherMethod.none;
    File _dictFile = null;
    Map<String, File> _scatterOutputs = new HashMap<>();
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

    public VariantProcessingJob(Container c, User user, @Nullable String jobName, PipeRoot pipeRoot, SequenceOutputHandler handler, List<SequenceOutputFile> files, JSONObject jsonParams, VariantProcessingStep.ScatterGatherMethod scatterGatherMethod) throws IOException, PipelineJobException
    {
        super(c, user, jobName, pipeRoot, handler, files, jsonParams);
        _scatterGatherMethod = scatterGatherMethod;

        if (isScatterJob())
        {
            validateScatterForTask();
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

    private void validateScatterForTask()
    {
        if (!isScatterJob())
        {
            return;
        }

        if (!(getHandler() instanceof VariantProcessingStep.SupportsScatterGather))
        {
            throw new IllegalArgumentException("Task doe not support Scatter/Gather: " + getHandler().getName());
        }


        VariantProcessingStep.SupportsScatterGather sg = (VariantProcessingStep.SupportsScatterGather)getHandler();
        sg.validateScatter(getScatterGatherMethod(), this);
    }

    private LinkedHashMap<String, List<Interval>> establishIntervals()
    {
        LinkedHashMap<String, List<Interval>> ret;
        SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(_dictFile.toPath());
        if (_scatterGatherMethod == VariantProcessingStep.ScatterGatherMethod.contig)
        {
            ret = new LinkedHashMap<>();
            for (SAMSequenceRecord rec : dict.getSequences())
            {
                ret.put(rec.getSequenceName(), Collections.singletonList(new Interval(rec.getSequenceName(), 1, rec.getSequenceLength())));
            }
        }
        else if (_scatterGatherMethod == VariantProcessingStep.ScatterGatherMethod.chunked)
        {
            int basesPerJob = getParameterJson().getInt("scatterGather.basesPerJob");
            boolean allowSplitChromosomes = doAllowSplitContigs();
            int maxContigsPerJob = getParameterJson().optInt("scatterGather.maxContigsPerJob", -1);
            getLogger().info("Creating jobs with target bp size: " + basesPerJob + " mbp.  allow splitting configs: " + allowSplitChromosomes + ", max contigs per job: " + maxContigsPerJob);

            basesPerJob = basesPerJob * 1000000;
            ret = ScatterGatherUtils.divideGenome(dict, basesPerJob, allowSplitChromosomes, maxContigsPerJob);

        }
        else if (_scatterGatherMethod == VariantProcessingStep.ScatterGatherMethod.fixedJobs)
        {
            long totalSize = dict.getReferenceLength();
            int numJobs = getParameterJson().getInt("scatterGather.totalJobs");
            int jobSize = (int)Math.ceil(totalSize / (double)numJobs);
            getLogger().info("Creating " + numJobs + " jobs with approximate size: " + jobSize + " bp.");
            ret = ScatterGatherUtils.divideGenome(dict, jobSize, true, -1);
        }
        else
        {
            throw new IllegalArgumentException("Unknown scatter type: " + _scatterGatherMethod.name());
        }

        return ret;
    }

    public boolean doAllowSplitContigs()
    {
        return getParameterJson().optBoolean("scatterGather.allowSplitChromosomes", true);
    }

    public boolean isScatterJob()
    {
        return _scatterGatherMethod != VariantProcessingStep.ScatterGatherMethod.none;
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
            final TableInfo ti = PipelineService.get().getJobsTable(getUser(), getContainer());
            for (String name : intervalMap.keySet())
            {
                // Check if exists and only create if needed:
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("JobParent"), getJobGUID());
                filter.addCondition(FieldKey.fromString("Description"), getChildJobName(this, name));
                TableSelector ts = new TableSelector(ti, filter, null);
                if (ts.exists())
                {
                    getLogger().debug("Child job already exists, will not re-create: " + getChildJobName(this, name));
                }
                else
                {
                    jobs.add(createSingleContigJob(name));
                }
            }

            return Collections.unmodifiableList(jobs);
        }

        return super.createSplitJobs();
    }

    private File getJobToIntervalFile()
    {
        return new File(isSplitJob() ? getDataDirectory().getParentFile() : getDataDirectory(), "jobsToInterval.txt");
    }

    private void writeJobToIntervalMap(Map<String, List<Interval>> jobToIntervalMap) throws IOException
    {
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(getJobToIntervalFile()), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            for (String name : jobToIntervalMap.keySet())
            {
                for (Interval i : jobToIntervalMap.get(name))
                {
                    writer.writeNext(new String[]{name, i.getContig(), String.valueOf(i.getStart()), String.valueOf(i.getEnd())});
                }
            }
        }
    }

    @JsonIgnore
    public Map<String, List<Interval>> getJobToIntervalMap()
    {
        if (_jobToIntervalMap == null)
        {
            File tsv = getJobToIntervalFile();
            try (CSVReader reader = new CSVReader(Readers.getReader(tsv), '\t'))
            {
                LinkedHashMap<String, List<Interval>> ret = new LinkedHashMap<>();
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    List<Interval> group = ret.getOrDefault(line[0], new ArrayList<>());
                    group.add(new Interval(line[1], Integer.parseInt(line[2]), Integer.parseInt(line[3])));

                    ret.put(line[0], group);
                }

                _jobToIntervalMap = ret;
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
            String jobName = getChildJobName(this, jobNameSuffix);

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
            getLogger().debug("Merging child job VCFs.  total: " + childJob.getScatterJobOutputs().size());
            _scatterOutputs.putAll(childJob.getScatterJobOutputs());
        }
    }

    public Map<String, File> getScatterJobOutputs()
    {
        return _scatterOutputs;
    }

    public void setScatterJobOutputs(Map<String, File> scatterOutputs)
    {
        _scatterOutputs = scatterOutputs;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return  PipelineJobService.get().getTaskPipeline(new TaskId(VariantProcessingJob.class));
    }

    public VariantProcessingStep.ScatterGatherMethod getScatterGatherMethod()
    {
        return _scatterGatherMethod;
    }

    public void setScatterGatherMethod(VariantProcessingStep.ScatterGatherMethod scatterGatherMethod)
    {
        _scatterGatherMethod = scatterGatherMethod;
    }

    public static class TestCase extends Assert
    {
        private static final Logger _log = LogManager.getLogger(SequenceAlignmentTask.TestCase.class);

        @Test
        public void serializeTest() throws Exception
        {
            VariantProcessingJob job1 = new VariantProcessingJob();
            job1._intervalSetName = "chr1";
            job1._scatterGatherMethod = VariantProcessingStep.ScatterGatherMethod.chunked;

            File tmp = new File(System.getProperty("java.io.tmpdir"));
            File xml = new File(tmp, "variantProcessingJob.txt");

            ObjectMapper objectMapper = PipelineJob.createObjectMapper();
            objectMapper.writeValue(xml, job1);

            VariantProcessingJob job2 = objectMapper.readValue(xml, VariantProcessingJob.class);
            assertEquals(job1.getIntervalSetName(), job2.getIntervalSetName());
            assertEquals(job1.getScatterGatherMethod(), job2.getScatterGatherMethod());

            xml.delete();
        }

        @Test
        public void intervalSerializeTest() throws Exception
        {
            VariantProcessingJob job1 = new VariantProcessingJob(){
                @Override
                public File getDataDirectory()
                {
                    return new File(System.getProperty("java.io.tmpdir"));
                }
            };

            job1._intervalSetName = "chr1";
            job1._scatterGatherMethod = VariantProcessingStep.ScatterGatherMethod.chunked;

            Map<String, List<Interval>> intervalMap = new LinkedHashMap<>();
            intervalMap.put("1", Arrays.asList(new Interval("chr1", 1, 10)));
            intervalMap.put("4", Arrays.asList(new Interval("chr4", 1, 10)));
            intervalMap.put("5", Arrays.asList(new Interval("chr5", 1, 10)));
            intervalMap.put("2", Arrays.asList(new Interval("chr2", 1, 10), new Interval("chr2", 11, 20), new Interval("chr2", 21, 400)));
            job1.writeJobToIntervalMap(intervalMap);
            job1._jobToIntervalMap = null;

            Map<String, List<Interval>> intervalMap2 = job1.getJobToIntervalMap();
            assertEquals(intervalMap, intervalMap2);
        }
    }

    public File getLocationForCachedInputs(WorkDirectory wd, boolean createIfDoesntExist)
    {
        File ret;

        String localDir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("LOCAL_DATA_CACHE_DIR");
        if (localDir == null)
        {
            localDir = StringUtils.trimToNull(System.getenv("LOCAL_DATA_CACHE_DIR"));
        }

        if (localDir == null)
        {
            ret = new File(wd.getDir(), "cachedData");
        }
        else
        {
            String guid = getParentGUID() == null ? getJobGUID() : getParentGUID();
            ret = new File(localDir, guid);

            getLogger().debug("Using local directory to cache data: " + ret.getPath());
        }

        if (createIfDoesntExist && !ret.exists())
        {
            ret.mkdirs();
        }

        return ret;
    }
}
