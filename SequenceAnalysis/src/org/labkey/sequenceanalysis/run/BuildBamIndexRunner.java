package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/2/12
 * Time: 10:40 AM
 */
public class BuildBamIndexRunner extends PicardRunner
{
    public BuildBamIndexRunner(Logger logger)
    {
        _logger = logger;
    }

    public String getOutputFilename(File file)
    {
        return file.getName() + ".bai";
    }

    public File execute(File file) throws PipelineJobException
    {
        _logger.info("Creating index for file : " + file.getPath());
        _logger.info("\tBuildBamIndex version: " + getVersion());

        doExecute(getWorkingDir(file), getParams(file));

        File output = new File(getWorkingDir(file), getOutputFilename(file));
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
        params.add("-jar");
        params.add(getJar().getPath());
        params.add("INPUT=" + file.getPath());
        params.add("OUTPUT=" + new File(getWorkingDir(file), getOutputFilename(file)).getPath());

        return params;
    }

    protected File getJar()
    {
        return getPicardJar("BuildBamIndex.jar");
    }
}
