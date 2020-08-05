package org.labkey.api.sequenceanalysis.run;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DISCVRSeqRunner extends AbstractCommandWrapper
{
    public DISCVRSeqRunner(Logger log)
    {
        super(log);
    }

    protected String getJarName()
    {
        return "DISCVRSeq.jar";
    }

    protected File getJar()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("DISCVRSEQPATH");
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

    protected List<String> getBaseArgs(String toolName)
    {
        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJava8FilePath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
        args.add("-jar");
        args.add(getJar().getPath());
        args.add(toolName);

        return args;
    }
}
