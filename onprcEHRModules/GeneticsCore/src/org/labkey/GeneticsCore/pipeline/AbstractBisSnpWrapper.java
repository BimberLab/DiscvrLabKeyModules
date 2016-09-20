package org.labkey.GeneticsCore.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.CreateSequenceDictionaryWrapper;

import java.io.File;

/**
 * Created by bimber on 8/14/2016.
 */
public class AbstractBisSnpWrapper extends AbstractCommandWrapper
{
    public AbstractBisSnpWrapper(Logger log)
    {
        super(log);
    }

    protected File getJAR()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("BISSNPPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("BisSNP.jar") : new File(path, "BisSNP.jar");
    }

    public boolean jarExists()
    {
        return getJAR() == null || !getJAR().exists();
    }

    protected void ensureDictionary(File referenceFasta) throws PipelineJobException
    {
        getLogger().info("\tensure dictionary exists");
        new CreateSequenceDictionaryWrapper(getLogger()).execute(referenceFasta, false);
    }

    protected String getJava6Filepath()
    {
        String javaDir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_HOME_6");
        if (javaDir != null)
        {
            File ret = new File(javaDir, "bin");
            ret = new File(ret, "java");
            return ret.getPath();
        }

        //if not explicitly found, try the default
        javaDir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_HOME");
        if (javaDir != null)
        {
            File ret = new File(javaDir, "bin");
            ret = new File(ret, "java");
            return ret.getPath();
        }

        return "java";
    }
}
