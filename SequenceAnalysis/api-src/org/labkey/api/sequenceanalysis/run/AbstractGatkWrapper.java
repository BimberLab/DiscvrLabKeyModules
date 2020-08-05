package org.labkey.api.sequenceanalysis.run;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.exception.ConvergenceException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
abstract public class AbstractGatkWrapper extends AbstractCommandWrapper
{
    protected Integer _minRamPerQueueJob = 2;
    protected Integer _maxRamOverride = null;

    public AbstractGatkWrapper(Logger log)
    {
        super(log);
    }

    protected String getJarName()
    {
        return "GenomeAnalysisTK.jar";
    }

    protected File getJAR()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("GATKPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File(getJarName()) : new File(path, getJarName());
    }

    public void setMaxRamOverride(Integer maxRamOverride)
    {
        _maxRamOverride = maxRamOverride;
    }

    protected void addJavaHomeToEnvironment()
    {
        //since GATK requires java8, set JAVA_HOME to match this:
        File java8 = new File(SequencePipelineService.get().getJava8FilePath()).getParentFile();
        if (java8.getParentFile() == null)
        {
            getLogger().debug("unexpected path to java8, cannot determine JAVA_HOME: " + java8.getPath());
            return;
        }

        String javaDir = java8.getParentFile().getPath();
        getLogger().debug("setting JAVA_HOME to java8 location: " + javaDir);
        addToEnvironment("JAVA_HOME", javaDir);
    }
    
    protected File getQueueJAR()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("GATKPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("Queue.jar") : new File(path, "Queue.jar");
    }

    public boolean jarExists()
    {
        return getJAR() == null || !getJAR().exists();
    }

    protected void ensureDictionary(File referenceFasta) throws PipelineJobException
    {
        getLogger().info("\tensure fasta index exists");
        SequenceAnalysisService.get().ensureFastaIndex(referenceFasta, getLogger());

        getLogger().info("\tensure dictionary exists");
        new CreateSequenceDictionaryWrapper(getLogger()).execute(referenceFasta, false);
    }

    public String getVersionString() throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJava8FilePath());
        args.addAll(SequencePipelineService.get().getJavaOpts(_maxRamOverride));
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("--version");

        return StringUtils.trimToNull(executeWithOutput(args));
    }

    public Integer getMinRamPerQueueJob()
    {
        return _minRamPerQueueJob;
    }

    public void setMinRamPerQueueJob(Integer minRamPerQueueJob)
    {
        _minRamPerQueueJob = minRamPerQueueJob;
    }

    protected Integer getScatterForQueueJob()
    {
        // NOTE: Queue will create n number of jobs, dividing memory evenly between them.  Because it is possible
        // to submit a job w/ lower available RAM and comparably high CPUs, this could result in queue not having enough memory per job.
        // therefore do a quick check and potentially scale down scatter
        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
        if (maxThreads != null)
        {
            if (_minRamPerQueueJob != null && _minRamPerQueueJob > 0)
            {
                String maxRamSetting = StringUtils.trimToNull(System.getenv("SEQUENCEANALYSIS_MAX_RAM"));
                if (maxRamSetting != null)
                {
                    try
                    {
                        Integer maxRamAllowed = ConvertHelper.convert(maxRamSetting, Integer.class);
                        if (maxRamAllowed != null)
                        {
                            int adjusted = Math.max(maxRamAllowed / _minRamPerQueueJob, 1);
                            if (adjusted < maxThreads)
                            {
                                getLogger().debug("lowering max threads to match available RAM.  setting to: " + adjusted);
                                maxThreads = adjusted;
                            }
                        }
                    }
                    catch (ConvergenceException e)
                    {
                        getLogger().warn("non-numeric value for SEQUENCEANALYSIS_MAX_RAM: [" + maxRamSetting + "]");
                    }
                }
            }
        }
        else
        {
            maxThreads = 1;
        }

        return maxThreads;
    }

    protected List<String> getBaseArgs()
    {
        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJava8FilePath());
        args.addAll(SequencePipelineService.get().getJavaOpts(_maxRamOverride));
        args.add("-jar");
        args.add(getJAR().getPath());

        return args;
    }
}
