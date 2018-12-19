package org.labkey.api.sequenceanalysis.run;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.exception.ConvergenceException;
import org.apache.log4j.Logger;
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

    private String getJava8FilePath()
    {
        //This should be defined at on TeamCity, and can be used on other servers if needed
        String java8Home = StringUtils.trimToNull(System.getenv("JDK_18"));
        if (java8Home != null)
        {
            File ret = new File(java8Home, "bin");
            ret = new File(ret, "java");
            return ret.getPath();
        }

        return SequencePipelineService.get().getJavaFilepath();
    }

    public String getVersionString() throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getJava8FilePath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
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
}
