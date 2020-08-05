package org.labkey.sequenceanalysis;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.RScriptEngineFactory;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.HasJobParams;
import org.labkey.api.sequenceanalysis.pipeline.JobResourceSettings;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.CommandWrapper;
import org.labkey.api.sequenceanalysis.run.CreateSequenceDictionaryWrapper;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.analysis.CellHashingHandler;
import org.labkey.sequenceanalysis.pipeline.PipelineStepCtxImpl;
import org.labkey.sequenceanalysis.pipeline.SequenceJob;
import org.labkey.sequenceanalysis.pipeline.SequenceOutputHandlerFinalTask;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.preprocessing.PreprocessingOutputImpl;
import org.labkey.sequenceanalysis.run.preprocessing.TrimmomaticWrapper;
import org.labkey.sequenceanalysis.run.util.BuildBamIndexWrapper;
import org.labkey.sequenceanalysis.run.util.SortVcfWrapper;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:45 PM
 */
public class SequencePipelineServiceImpl extends SequencePipelineService
{
    private static SequencePipelineServiceImpl _instance = new SequencePipelineServiceImpl();

    private static final Logger _log = LogManager.getLogger(SequencePipelineServiceImpl.class);
    private Set<PipelineStepProvider> _providers = new HashSet<>();
    private Set<JobResourceSettings> _resourceSettings = new HashSet<>();

    private SequencePipelineServiceImpl()
    {

    }

    public static SequencePipelineServiceImpl get()
    {
        return _instance;
    }

    @Override
    public void registerPipelineStep(PipelineStepProvider provider)
    {
        _log.info("registering sequence pipeline provider: " + provider.getName());
        _providers.add(provider);
    }

    @Override
    public Set<PipelineStepProvider> getAllProviders()
    {
        return Collections.unmodifiableSet(_providers);
    }

    @Override
    public <StepType extends PipelineStep> Set<PipelineStepProvider<StepType>> getProviders(Class<? extends StepType> stepType)
    {
        Set<PipelineStepProvider<StepType>> ret = new HashSet<>();
        for (PipelineStepProvider provider : _providers)
        {
            ParameterizedType parameterizedType = (ParameterizedType)provider.getClass().getGenericSuperclass();
            Class clazz = (Class)parameterizedType.getActualTypeArguments()[0];

            if (stepType.isAssignableFrom(clazz))
            {
                ret.add(provider);
            }
        }

        return ret;
    }

    @Override
    public <StepType extends PipelineStep> PipelineStepProvider<StepType> getProviderByName(String name, Class<? extends StepType> stepType)
    {
        if (StringUtils.trimToNull(name) == null)
        {
            throw new IllegalArgumentException("PipelineStepProvider name cannot be empty");
        }

        for (PipelineStepProvider provider : getProviders(stepType))
        {
            if (name.equals(provider.getName()))
            {
                return provider;
            }
        }

        throw new IllegalArgumentException("Unable to find pipeline step: [" + name + "]");
    }

    @Override
    public File getExeForPackage(String packageName, String exe)
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(packageName);
        if (StringUtils.trimToNull(path) != null)
        {
            return new File(path, exe);
        }
        else
        {
            path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SEQUENCE_TOOLS_PARAM);
            path = StringUtils.trimToNull(path);
            if (path != null)
            {
                File ret = new File(path, exe);
                if (ret.exists())
                    return ret;
            }
            else
            {
                path = PipelineJobService.get().getAppProperties().getToolsDirectory();
                path = StringUtils.trimToNull(path);
                if (path != null)
                {
                    File ret = new File(path, exe);
                    if (ret.exists())
                        return ret;
                }
            }

            return new File(exe);
        }
    }

    public <StepType extends PipelineStep> List<PipelineStepCtx<StepType>> getSteps(PipelineJob job, Class<StepType> stepType)
    {
        Map<String, String> params;
        if (job instanceof HasJobParams)
        {
            params = ((HasJobParams)job).getJobParams();
        }
        else
        {
            params = job.getParameters();
        }

        PipelineStep.StepType type = PipelineStep.StepType.getStepType(stepType);
        if (!params.containsKey(type.name()) || StringUtils.isEmpty(params.get(type.name())))
        {
            return Collections.EMPTY_LIST;
        }

        List<PipelineStepCtx<StepType>> steps = new ArrayList<>();
        String[] pipelineSteps = params.get(type.name()).split(";");

        List<String> encounteredStepNames = new ArrayList<>();
        for (String stepName : pipelineSteps)
        {
            PipelineStepProvider<StepType> provider = SequencePipelineService.get().getProviderByName(stepName, stepType);
            int stepIdx = Collections.frequency(encounteredStepNames, provider.getName());

            steps.add(new PipelineStepCtxImpl<>(provider, stepIdx));

            encounteredStepNames.add(provider.getName());
        }

        return steps;
    }

    @Override
    public void ensureSequenceDictionaryExists(File referenceFasta, Logger log, boolean forceRecreate) throws PipelineJobException
    {
        new CreateSequenceDictionaryWrapper(log).execute(referenceFasta, false);
    }

    @Override
    public String getUnzippedBaseName(String filename)
    {
        filename = filename.replaceAll("\\.gz$", "");
        return FilenameUtils.getBaseName(filename);
    }

    @Override
    public String getJavaFilepath()
    {
        String javaDir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_HOME");
        if (javaDir == null)
        {
            javaDir = StringUtils.trimToNull(System.getenv("JAVA_HOME"));
        }

        if (javaDir != null)
        {
            File ret = new File(javaDir, "bin");
            ret = new File(ret, "java");
            return ret.getPath();
        }
        else
        {
            return "java";
        }
    }

    public String getJava8FilePath()
    {
        String java8Home = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_HOME_8");
        if (java8Home == null)
        {
            //This should be defined at on TeamCity, and can be used on other servers if needed
            java8Home = StringUtils.trimToNull(System.getenv("JDK_18"));
        }

        if (java8Home != null)
        {
            File ret = new File(java8Home, "bin");
            ret = new File(ret, "java");
            return ret.getPath();
        }

        return SequencePipelineService.get().getJavaFilepath();
    }

    @Override
    public String getJavaTempDir()
    {
        String tmpDir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_TMP_DIR");
        if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
        {
            tmpDir = System.getProperty("java.io.tmpdir");
            if (tmpDir != null)
                _log.debug("setting temp directory to: " + tmpDir);
        }

        return tmpDir;
    }

    @Override
    public Integer getMaxRam()
    {
        String maxRamStr = StringUtils.trimToNull(System.getenv("SEQUENCEANALYSIS_MAX_RAM"));
        if (maxRamStr == null)
        {
            maxRamStr = StringUtils.trimToNull(PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("SEQUENCEANALYSIS_MAX_RAM"));
        }

        Integer maxRam = null;
        if (maxRamStr != null)
        {
            maxRam = "-1".equals(maxRamStr) ? null : Integer.parseInt(maxRamStr);
        }

        return maxRam;
    }

    @Override
    public List<String> getJavaOpts()
    {
        return getJavaOpts(null);
    }

    @Override
    public List<String> getJavaOpts(@Nullable Integer maxRamOverride)
    {
        List<String> params = new ArrayList<>();
        String tmpDir = getJavaTempDir();
        if (StringUtils.trimToNull(tmpDir) != null)
        {
            params.add("-Djava.io.tmpdir=" + tmpDir);
        }

        //try environment first:

        Integer maxRam = getMaxRam();
        if (maxRamOverride != null)
        {
            params.add("-Xmx" + maxRamOverride + "g");
            params.add("-Xms" + maxRamOverride + "g");
        }
        else if (maxRam != null)
        {
            params.add("-Xmx" + maxRam + "g");
            params.add("-Xms" + maxRam + "g");
        }

        String otherOpts = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("SEQUENCEANALYSIS_JAVA_OPTS");
        if (StringUtils.trimToNull(otherOpts) != null)
        {
            String[] tokens = otherOpts.split(" ");
            params.addAll(Arrays.asList(tokens));
        }

        return params;
    }

    @Override
    public File getRemoteGenomeCacheDirectory()
    {
        String dir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("REMOTE_GENOME_CACHE_DIR");
        if (StringUtils.trimToNull(dir) != null)
        {
            File ret = new File(dir);
            if (ret.exists())
            {
                return ret;
            }
        }

        return null;
    }

    @Override
    public Integer getMaxThreads(Logger log)
    {
        return SequenceTaskHelper.getMaxThreads(log);
    }

    @Override
    public CommandWrapper getCommandWrapper(Logger log)
    {
        return new AbstractCommandWrapper(log){

        };
    }

    @Override
    public List<File> getSequenceJobInputFiles(PipelineJob job)
    {
        if (!(job instanceof SequenceJob))
        {
            return null;
        }

        return ((SequenceJob) job).getInputFiles();
    }

    public Integer getExpRunIdForJob(PipelineJob job) throws PipelineJobException
    {
        return SequenceTaskHelper.getExpRunIdForJob(job);
    }

    public long getLineCount(File f) throws PipelineJobException
    {
        return SequenceUtil.getLineCount(f);
    }

    public File ensureBamIndex(File inputBam, Logger log, boolean forceDeleteExisting) throws PipelineJobException
    {
        File expectedIndex = new File(inputBam.getPath() + ".bai");
        if (expectedIndex.exists() && (expectedIndex.lastModified() < inputBam.lastModified() || forceDeleteExisting))
        {
            log.info("deleting out of date index: " + expectedIndex.getPath());
            expectedIndex.delete();
        }

        if (!expectedIndex.exists())
        {
            log.debug("\tcreating temp index for BAM: " + inputBam.getName());
            BuildBamIndexWrapper buildBamIndexWrapper = new BuildBamIndexWrapper(log);
            buildBamIndexWrapper.setStringency(ValidationStringency.SILENT);
            buildBamIndexWrapper.executeCommand(inputBam);

            return expectedIndex;
        }
        else
        {
            log.debug("BAM index already exists: " + expectedIndex.getPath());
        }

        return null;
    }

    public SAMFileHeader.SortOrder getBamSortOrder(File bam) throws IOException
    {
        return SequenceUtil.getBamSortOrder(bam);
    }

    public File sortVcf(File inputVcf, @Nullable File outputVcf, File sequenceDictionary, Logger log) throws PipelineJobException
    {
        SortVcfWrapper wrapper = new SortVcfWrapper(log);
        return wrapper.sortVcf(inputVcf, outputVcf, sequenceDictionary);
    }

    public void sortROD(File input, Logger log, Integer startColumnIdx) throws IOException, PipelineJobException
    {
        SequenceUtil.sortROD(input, log, startColumnIdx);
    }

    @Override
    public String inferRPath(Logger log)
    {
        String path;

        //preferentially use R config setup in scripting props.  only works if running locally.
        if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
        {
            LabkeyScriptEngineManager svc = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);
            for (ExternalScriptEngineDefinition def : svc.getEngineDefinitions())
            {
                if (RScriptEngineFactory.isRScriptEngine(def.getExtensions()))
                {
                    path = new File(def.getExePath()).getParent();
                    log.info("Using RSciptEngine path: " + path);
                    return path;
                }
            }
        }

        //then pipeline config
        String packagePath = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("R");
        if (StringUtils.trimToNull(packagePath) != null)
        {
            log.info("Using path from pipeline config: " + packagePath);
            return packagePath;
        }

        //then RHOME
        Map<String, String> env = System.getenv();
        if (env.containsKey("RHOME"))
        {
            log.info("Using path from RHOME: " + env.get("RHOME"));
            return env.get("RHOME");
        }

        //else assume it's in the PATH
        log.info("Unable to infer R path, using null");

        return null;
    }

    @Override
    public void registerResourceSettings(JobResourceSettings settings)
    {
        _resourceSettings.add(settings);
    }

    public Set<JobResourceSettings> getResourceSettings()
    {
        return _resourceSettings;
    }

    @Override
    public Map<String, Object> getQualityMetrics(File fastq, Logger log)
    {
        return FastqUtils.getQualityMetrics(fastq, log);
    }

    @Override
    public boolean hasMinLineCount(File f, long minLines) throws PipelineJobException
    {
        return SequenceUtil.hasMinLineCount(f, minLines);
    }

    @Override
    public void updateOutputFile(SequenceOutputFile o, PipelineJob job, Integer runId, Integer analysisId)
    {
        SequenceOutputHandlerFinalTask.updateOutputFile(o, job, runId, analysisId);
    }

    @Override
    public PreprocessingStep.Output simpleTrimFastqPair(File fq1, File fq2, List<String> additionalParams, File outDir, Logger log) throws PipelineJobException
    {
        TrimmomaticWrapper wrapper = new TrimmomaticWrapper(log);
        wrapper.setOutputDir(outDir);
        wrapper.doTrim(wrapper.getTrimmomaticParams(fq1, fq2, "trim", additionalParams));

        PreprocessingOutputImpl output = new PreprocessingOutputImpl(fq1, fq2);
        List<File> files = wrapper.getExpectedOutputFilenames(fq1, fq2, "trim");
        for (File f : files)
        {
            if (!f.exists())
            {
                throw new PipelineJobException("Output file could not be found: " + f.getPath());
            }
            else if (!SequenceUtil.hasLineCount(f))
            {
                log.info("\tdeleting empty file: " + f.getName());
                f.delete();
            }
            else
            {
                output.addIntermediateFile(f);
            }
        }

        if (fq2 == null)
        {
            output.setProcessedFastq(Pair.of(files.get(0), null));
        }
        else
        {
            output.setProcessedFastq(Pair.of(files.get(0), files.get(2)));
        }

        return output;
    }

    @Override
    public File runCiteSeqCount(PipelineStepOutput output, @Nullable String outputCategory, Readset htoReadset, File htoList, File cellBarcodeList, File outputDir, String basename, Logger log, List<String> extraArgs, boolean doHtoFiltering, @Nullable Integer minCountPerCell, File localPipelineDir, @Nullable Integer editDistance, boolean scanEditDistances, Readset parentReadset, @Nullable Integer genomeId, boolean generateHtoCalls) throws PipelineJobException
    {
        CellHashingHandler handler = new CellHashingHandler();

        return handler.runCiteSeqCount(output, outputCategory, htoReadset, htoList, cellBarcodeList, outputDir, basename, log, extraArgs, doHtoFiltering, minCountPerCell, localPipelineDir, editDistance, scanEditDistances, parentReadset, genomeId, generateHtoCalls ? CellHashingHandler.BARCODE_TYPE.hashing : CellHashingHandler.BARCODE_TYPE.citeseq);
    }
}
