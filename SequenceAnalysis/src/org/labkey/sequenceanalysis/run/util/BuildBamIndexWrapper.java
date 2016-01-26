package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * User: bimber
 * Date: 11/2/12
 * Time: 10:40 AM
 */
public class BuildBamIndexWrapper extends PicardWrapper
{
    public BuildBamIndexWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public String getOutputFilename(File file)
    {
        return file.getName() + ".bai";
    }

    public File executeCommand(File file) throws PipelineJobException
    {
        Date start = new Date();
        getLogger().info("Creating index for file : " + file.getPath());
        getLogger().info("\tBuildBamIndex version: " + getVersion());

        super.execute(getParams(file));

        File output = new File(getOutputDir(file), getOutputFilename(file));
        if (!output.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + output.getPath());
        }

        getLogger().info("\tBuildBamIndex duration: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true));

        return output;
    }

    private List<String> getParams(File file) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getPicardJar().getPath());
        params.add(getTooName());
        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        params.add("INPUT=" + file.getPath());
        params.add("OUTPUT=" + new File(getOutputDir(file), getOutputFilename(file)).getPath());

        return params;
    }

    protected String getTooName()
    {
        return "BuildBamIndex";
    }
}
