package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * User: bimber
 * Date: 6/24/2014
 * Time: 4:08 PM
 */
public class SamFormatConverterWrapper extends PicardWrapper
{
    public SamFormatConverterWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File execute(File inputFile, File outputFile, boolean deleteInput) throws PipelineJobException
    {
        Date start = new Date();
        getLogger().info("Converting file: " + inputFile.getPath());
        getLogger().info("\tto file: " + outputFile.getPath());

        execute(getParams(inputFile, outputFile));
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + outputFile.getPath());
        }
        else if (deleteInput)
        {
            inputFile.delete();
        }

        getLogger().info("\tSamFormatConverter duration: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true));

        return outputFile;
    }

    private List<String> getParams(File inputFile, File outputFile) throws PipelineJobException
    {
        List<String> params = getBaseArgs();
        inferMaxRecordsInRam(params);
        params.add("INPUT=" + inputFile.getPath());
        params.add("OUTPUT=" + outputFile.getPath());

        return params;
    }

    @Override
    protected String getToolName()
    {
        return "SamFormatConverter";
    }
}
