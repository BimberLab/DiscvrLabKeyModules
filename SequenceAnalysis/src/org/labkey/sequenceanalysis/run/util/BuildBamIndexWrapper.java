package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
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
        getLogger().info("Creating index for file : " + file.getPath());
        getLogger().info("\tBuildBamIndex version: " + getVersion());

        super.execute(getParams(file));

        File output = new File(getOutputDir(file), getOutputFilename(file));
        if (!output.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + output.getPath());
        }
        return output;
    }

    private List<String> getParams(File file) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.addAll(getBaseParams());
        params.add("-jar");
        params.add(getJar().getPath());
        params.add("INPUT=" + file.getPath());
        params.add("OUTPUT=" + new File(getOutputDir(file), getOutputFilename(file)).getPath());

        return params;
    }

    protected File getJar()
    {
        return getPicardJar("BuildBamIndex.jar");
    }
}
