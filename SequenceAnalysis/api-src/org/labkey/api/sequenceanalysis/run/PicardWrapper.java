package org.labkey.api.sequenceanalysis.run;

import htsjdk.samtools.ValidationStringency;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ConvertHelper;
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
        params.add(getPicardJar().getPath());
        params.add(getTooName());
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

    public File getPicardJar()
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

        File baseDir = path == null ? null : new File(path);

        return new File(baseDir, "picard.jar");
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

    abstract protected String getTooName();

    protected void inferMaxRecordsInRam(List<String> args)
    {
        if (args == null)
        {
            return;
        }

        for (String arg : args)
        {
            if (arg.startsWith("-Xmx") && arg.endsWith("g"))
            {
                String val = arg.substring(4, arg.length() - 1);
                Integer gb = ConvertHelper.convert(val, Integer.class);

                //A rule of thumb for reads of ~100bp is to set MAX_RECORDS_IN_RAM to be 250,000 reads per each GB given to the -Xmx
                args.add("MAX_RECORDS_IN_RAM=" + String.valueOf(gb * 250000));

                break;
            }
        }
    }
}
