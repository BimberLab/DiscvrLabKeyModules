package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;

import java.io.File;
import java.util.Date;
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

    private List<String> getParams(File file)
    {
        List<String> params = getBaseArgs();

        params.add("-INPUT");
        params.add(file.getPath());

        params.add("-OUTPUT");
        params.add(new File(getOutputDir(file), getOutputFilename(file)).getPath());

        return params;
    }

    @Override
    protected String getToolName()
    {
        return "BuildBamIndex";
    }
}
