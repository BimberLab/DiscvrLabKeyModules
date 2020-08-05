package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: bimber
 * Date: 11/20/12
 * Time: 9:00 PM
 */
public class SFFExtractRunner extends AbstractCommandWrapper
{
    public SFFExtractRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public String getOutputFilename(File file)
    {
        return FileUtil.getBaseName(file) + ".fastq";
    }

    public File convert(File input) throws PipelineJobException
    {
        File output = new File(getOutputDir(input), getOutputFilename(input));
        return convert(input, output);
    }

    public File convert(File input, File output) throws PipelineJobException
    {
        getLogger().info("Converting SFF file to FASTQ");
        getLogger().info("\tSFF extract version: " + getVersion());

        execute(getParams(input, output));

        if (!output.exists())
        {
            throw new PipelineJobException("No file created, expected: " + output.getPath());
        }
        return output;
    }

    private String getExePath()
    {
        return SequencePipelineService.get().getExeForPackage("SEQCRUMBSPATH", "sff_extract").getPath();
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

    public String getVersion() throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add(getExePath());
        params.add("--version");

        String output = executeWithOutput(params);
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
