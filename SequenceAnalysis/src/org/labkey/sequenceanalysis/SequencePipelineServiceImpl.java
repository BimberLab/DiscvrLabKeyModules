package org.labkey.sequenceanalysis;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.CommandWrapper;
import org.labkey.api.sequenceanalysis.run.CreateSequenceDictionaryWrapper;
import org.labkey.sequenceanalysis.pipeline.SequenceAnalysisJob;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:45 PM
 */
public class SequencePipelineServiceImpl extends SequencePipelineService
{
    private static SequencePipelineServiceImpl _instance = new SequencePipelineServiceImpl();

    private static final Logger _log = Logger.getLogger(SequencePipelineServiceImpl.class);
    private Set<PipelineStepProvider> _providers = new HashSet<>();

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

    public <StepType extends PipelineStep> List<PipelineStepProvider<StepType>> getSteps(PipelineJob job, Class<StepType> stepType)
    {
        PipelineStep.StepType type = PipelineStep.StepType.getStepType(stepType);
        if (!job.getParameters().containsKey(type.name()) || StringUtils.isEmpty(job.getParameters().get(type.name())))
        {
            return Collections.EMPTY_LIST;
        }

        List<PipelineStepProvider<StepType>> providers = new ArrayList<>();
        String[] pipelineSteps = job.getParameters().get(type.name()).split(";");
        for (String stepName : pipelineSteps)
        {
            providers.add(SequencePipelineService.get().getProviderByName(stepName, stepType));
        }

        return providers;
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

    public List<String> getJavaOpts()
    {
        List<String> params = new ArrayList<>();

        String tmpDir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_TMP_DIR");
        if (StringUtils.trimToNull(tmpDir) != null)
        {
            params.add("-Djava.io.tmpdir=" + tmpDir);
        }

        String xmx = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_MEMORY");
        if (StringUtils.trimToNull(xmx) != null)
        {
            String[] tokens = xmx.split(" ");
            params.addAll(Arrays.asList(tokens));
        }

        return params;
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
        if (!(job instanceof SequenceAnalysisJob))
        {
            return null;
        }

        SequenceAnalysisJob pipelineJob = (SequenceAnalysisJob)job;
        List<File> ret = new ArrayList<>();

        List<SequenceReadsetImpl> readsets = pipelineJob.getCachedReadsetModels();
        for (SequenceReadsetImpl rs : readsets)
        {
            //NOTE: because these jobs can be split, we only process the readsets we chose to include
            if (pipelineJob.getReadsetIdToProcesss() != null && !pipelineJob.getReadsetIdToProcesss().contains(rs.getReadsetId()))
            {
                continue;
            }

            for (ReadDataImpl d : rs.getReadData())
            {
                if (d.getFile1() != null)
                {
                    ret.add(d.getFile1());
                }

                if (d.getFile2() != null)
                {
                    ret.add(d.getFile2());
                }
            }
        }

        return ret;
    }
}
