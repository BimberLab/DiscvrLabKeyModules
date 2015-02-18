package org.labkey.sequenceanalysis;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
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
        Logger.getRootLogger().info("registering pipeline step: " + stepType.getName());

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
}
