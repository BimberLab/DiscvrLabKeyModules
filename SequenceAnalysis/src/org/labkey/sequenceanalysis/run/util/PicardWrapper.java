package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.ValidationStringency;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * User: bimber
 * Date: 12/14/12
 * Time: 6:01 AM
 */
abstract public class PicardWrapper extends AbstractCommandWrapper
{
    private ValidationStringency _stringency = ValidationStringency.LENIENT;

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

        boolean origWarn = isWarnNonZeroExits();
        boolean origThrow = isThrowNonZeroExits();
        setWarnNonZeroExits(false);
        setThrowNonZeroExits(false);

        String ret = StringUtils.trim(execute(params));
        setWarnNonZeroExits(origWarn);
        setThrowNonZeroExits(origThrow);

        return ret;
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

    public static List<String> getBaseParams()
    {
        List<String> ret = new ArrayList<>();

        String tmpDir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_TMP_DIR");
        if (StringUtils.trimToNull(tmpDir) != null)
        {
            ret.add("-Djava.io.tmpdir=" + tmpDir);
        }

        String xmx = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_MEMORY");
        if (StringUtils.trimToNull(xmx) != null)
        {
            String[] tokens = xmx.split(" ");
            ret.addAll(Arrays.asList(tokens));
        }

        return ret;
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
