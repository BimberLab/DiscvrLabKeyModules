package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
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

        return outputFile;
    }

    private List<String> getParams(File inputFile, File outputFile) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-Xmx4g");
        params.add("-jar");
        params.add(getJar().getPath());
        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        params.add("INPUT=" + inputFile.getPath());
        params.add("OUTPUT=" + outputFile.getPath());

        return params;
    }

    @Override
    protected File getJar()
    {
        return getPicardJar("SamFormatConverter.jar");
    }
}
