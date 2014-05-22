package org.labkey.sequenceanalysis.run;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.pipeline.PipelineJobService;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/14/12
 * Time: 6:01 AM
 */
abstract public class PicardRunner extends AbstractRunner
{
    public String getVersion()
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-jar");
        params.add(getJar().getPath());
        params.add("--version");

        return StringUtils.trim(runCommand(params));
    }

    public File getPicardJar(String jarName)
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("PICARDPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        String subdir = "picard-tools";
        File baseDir = path == null ? new File(subdir) : new File(path, subdir);

        return new File(baseDir, jarName);
    }

    abstract protected File getJar();
}
