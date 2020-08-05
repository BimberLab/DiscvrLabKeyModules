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
        List<String> params = new LinkedList<>();
        params.add(SequencePipelineService.get().getJava8FilePath());
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

    public static File getPicardJar()
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

        return path == null ? null : new File(path, "picard.jar");
    }

    protected File getJar()
    {
        return getPicardJar();
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

    protected List<String> getBaseArgs()
    {
        List<String> params = new LinkedList<>();
        params.add(SequencePipelineService.get().getJava8FilePath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getJar().getPath());
        params.add(getToolName());
        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        params.add("COMPRESSION_LEVEL=" + getCompressionLevel());

        //note: having issues, probably due to OS versions
        params.add("USE_JDK_DEFLATER=true");
        params.add("USE_JDK_INFLATER=true");

        return params;
    }

    protected void inferMaxRecordsInRam(List<String> args)
    {
        //TODO: this is temporarily disabled since it does not seem to make much of a difference, and can be counter productive.
        //if this remains out, calls to this method should be removed
        return;

//        if (args == null)
//        {
//            return;
//        }
//
//        for (String arg : args)
//        {
//            if (arg.startsWith("-Xmx") && arg.endsWith("g"))
//            {
//                String val = arg.substring(4, arg.length() - 1);
//                Integer gb = ConvertHelper.convert(val, Integer.class);
//
//                //A rule of thumb for reads of ~100bp is to set MAX_RECORDS_IN_RAM to be 250,000 reads per each GB given to the -Xmx
//                args.add("MAX_RECORDS_IN_RAM=" + String.valueOf(gb * 250000));
//
//                break;
//            }
//        }
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
