package org.labkey.sequenceanalysis.run;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/29/12
 * Time: 7:55 PM
 */
public class FixMateInformationRunner extends PicardRunner
{
    public FixMateInformationRunner(Logger logger)
    {
        _logger = logger;
    }

    public String getOutputFilename(File file)
    {
        return file.getName() + ".tmp";
    }

    public File execute(File file) throws PipelineJobException
    {
        _logger.info("Fixing Mate Information: " + file.getPath());
        _logger.info("\tFixMateInformation version: " + getVersion());

        doExecute(getWorkingDir(file), getParams(file));
        File output = new File(getWorkingDir(file), getOutputFilename(file));
        if (!output.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + output.getPath());
        }

        try
        {
            if (!file.delete() || file.exists())
                throw new PipelineJobException("File exists: " + file.getPath());
            FileUtils.moveFile(output, file);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e.getMessage());
        }

        return file;
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
        return new File(PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("PICARDPATH"), "FixMateInformation.jar");
    }
}