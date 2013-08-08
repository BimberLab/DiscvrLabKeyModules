package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/20/12
 * Time: 9:00 PM
 */
public class SFFExtractRunner extends AbstractRunner
{
    public SFFExtractRunner(Logger logger)
    {
        _logger = logger;
    }

    public String getOutputFilename(File file)
    {
        return FileUtil.getBaseName(file) + ".fastq";
    }

    public File convert(File input) throws PipelineJobException
    {
        File output = new File(getWorkingDir(input), getOutputFilename(input));
        return convert(input, output);
    }

    public File convert(File input, File output) throws PipelineJobException
    {
        _logger.info("Converting SFF file to FASTQ");
        _logger.info("\tSFF extract version: " + getVersion());

        doExecute(getWorkingDir(input), getParams(input, output));

        if (!output.exists())
        {
            throw new PipelineJobException("No file created, expected: " + output.getPath());
        }
        return output;
    }

    private String getExePath()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("SEQCRUMBSPATH");
        if (path == null)
            return new File(path, "sff_extract").getPath();
        else
            return "sff_extract";

    }

    private List<String> getParams(File input, File output) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add(getExePath());

        params.add("-o");
        params.add(output.getPath());

        params.add("-c");

        //added to avoid warnings if all sequences begin with the same sequence
        params.add("--max_percentage");
        params.add("100");

        params.add(input.getPath());

        return params;
    }

    public String getVersion()
    {
        List<String> params = new LinkedList<>();
        params.add(getExePath());
        params.add("--version");

        String output = runCommand(params);
        Pattern pattern = Pattern.compile("([.|\\d]+)", Pattern.MULTILINE);
        Matcher m = pattern.matcher(output);
        if (m.find())
        {
            int start = m.start();
            int end = m.end();
            return output.substring(start, end);
        }
        return "Version not known";
    }
}
