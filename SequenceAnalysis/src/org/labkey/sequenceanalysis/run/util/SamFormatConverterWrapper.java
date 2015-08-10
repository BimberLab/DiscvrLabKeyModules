package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
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
        List<String> params = new LinkedList<>();
        params.add("java");
        params.addAll(getBaseParams());
        params.add("-jar");
        params.add(getPicardJar().getPath());
        params.add(getTooName());
        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        inferMaxRecordsInRam(params);
        params.add("INPUT=" + inputFile.getPath());
        params.add("OUTPUT=" + outputFile.getPath());

        return params;
    }

    @Override
    protected String getTooName()
    {
        return "SamFormatConverter";
    }
}
