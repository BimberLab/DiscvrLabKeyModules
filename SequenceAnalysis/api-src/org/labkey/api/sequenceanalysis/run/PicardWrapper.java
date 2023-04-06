package org.labkey.api.sequenceanalysis.run;

import htsjdk.samtools.ValidationStringency;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;

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
    private ValidationStringency _stringency = ValidationStringency.LENIENT;
    private int _compressionLevel = 9;

    public PicardWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public String getVersion() throws PipelineJobException
    {
        if (!jarExists())
        {
            throw new PipelineJobException("Unable to find picard.jar");
        }

        List<String> params = new LinkedList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());
        params.add("-jar");
        params.add(getJar().getPath());
        params.add(getToolName());
        params.add("--version");

        boolean origWarn = isWarnNonZeroExits();
        boolean origThrow = isThrowNonZeroExits();
        setWarnNonZeroExits(false);
        setThrowNonZeroExits(false);

        String ret = StringUtils.trim(executeWithOutput(params));
        setWarnNonZeroExits(origWarn);
        setThrowNonZeroExits(origThrow);

        return ret;
    }

    public static @Nullable File getPicardJar(boolean throwIfNotFound)
    {
        return resolveFileInPath("picard.jar", "PICARDPATH", throwIfNotFound);
    }

    public boolean jarExists()
    {
        File jar = getPicardJar(false);
        return jar != null && jar.exists();
    }

    protected File getJar()
    {
        return getPicardJar(false);
    }

    public ValidationStringency getStringency()
    {
        return _stringency;
    }

    public void setStringency(ValidationStringency stringency)
    {
        _stringency = stringency;
    }

    abstract protected String getToolName();

    protected List<String> getBaseArgs() throws PipelineJobException
    {
        return getBaseArgs(false);
    }

    protected List<String> getBaseArgs(boolean basicArgsOnly) throws PipelineJobException
    {
        if (!jarExists())
        {
            throw new PipelineJobException("Unable to find picard.jar");
        }

        List<String> params = new LinkedList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getJar().getPath());
        params.add(getToolName());

        if (!basicArgsOnly)
        {
            params.add("--VALIDATION_STRINGENCY");
            params.add(getStringency().name());

            params.add("--COMPRESSION_LEVEL");
            params.add(String.valueOf(getCompressionLevel()));

            //note: having issues, probably due to OS versions
            params.add("--USE_JDK_DEFLATER");
            params.add("true");
        }

        return params;
    }

    public int getCompressionLevel()
    {
        return _compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel)
    {
        _compressionLevel = compressionLevel;
    }
}
