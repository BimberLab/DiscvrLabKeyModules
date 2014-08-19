package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.run.AbstractCommandWrapper;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * User: bimber
 * Date: 12/14/12
 * Time: 6:01 AM
 */
abstract public class PicardWrapper extends AbstractCommandWrapper
{
    private ValidationStringency _stringency = ValidationStringency.STRICT;

    public PicardWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public String getVersion() throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-jar");
        params.add(getJar().getPath());
        params.add("--version");

        return StringUtils.trim(execute(params));
    }

    public File getPicardJar(String jarName)
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("PICARDPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        String subdir = "picard-tools";
        File baseDir = path == null ? new File(subdir) : new File(path, subdir);

        return new File(baseDir, jarName);
    }

    public ValidationStringency getStringency()
    {
        return _stringency;
    }

    public void setStringency(ValidationStringency stringency)
    {
        _stringency = stringency;
    }

    abstract protected File getJar();
}
