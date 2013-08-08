package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/20/12
 * Time: 7:45 PM
 */
public class SeqCrumbsRunner extends AbstractRunner
{
    public SeqCrumbsRunner(Logger logger)
    {
        _logger = logger;
    }

    public String getOutputFilename(File file)
    {
        throw new UnsupportedOperationException("Not supported for SeqCrumbsRunner");
    }

    public File convertFormat(File input, File output) throws PipelineJobException
    {
        _logger.info("Converting Files to FASTQ");
        _logger.info("\tSeqCrumbs version: " + getVersion());

        doExecute(getWorkingDir(output), getParamsForConvertFormat(output, input));
        if (!output.exists())
        {
            throw new PipelineJobException("No file created, expected: " + output.getPath());
        }
        return output;
    }

    private List<String> getParamsForConvertFormat(File output, File file) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add(getExePath());
        params.add("-o");
        params.add(output.getPath());

        params.add("-f");
        params.add("fastq");

        params.add(file.getPath());

        return params;
    }

    private String getExePath()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("SEQCRUMBSPATH");
        if (path == null)
            return new File(path, "convert_format").getPath();
        else
            return "convert_format";

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