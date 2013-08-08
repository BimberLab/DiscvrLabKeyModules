package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/28/12
 * Time: 10:28 PM
 */
public class DownsampleSamRunner extends PicardRunner
{
    public DownsampleSamRunner(Logger logger)
    {
        _logger = logger;
    }

    public File execute(File file, Double pctRetained) throws PipelineJobException
    {
        _logger.info("Downsampling reads: " + file);
        _logger.info("\tPercent retained: " + pctRetained);
        _logger.info("\tDownsampleSam version: " + getVersion());

        doExecute(getWorkingDir(file), getParams(file, pctRetained));
        File output = new File(getWorkingDir(file), getOutputFilename(file));
        if (!output.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + output.getPath());
        }

        return output;

    }

    private List<String> getParams(File file, Double pctRetained) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-jar");
        params.add(getJar().getPath());

        params.add("INPUT=" + file.getPath());
        params.add("OUTPUT=" + new File(getWorkingDir(file), getOutputFilename(file)).getPath());
        params.add("PROBABILITY=" + pctRetained);

        return params;
    }

    public String getOutputFilename(File file)
    {
        return FileUtil.getBaseName(file) + ".downsampled.sam";
    }

    protected File getJar()
    {
        return new File(PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("PICARDPATH"), "DownsampleSam.jar");
    }
}
