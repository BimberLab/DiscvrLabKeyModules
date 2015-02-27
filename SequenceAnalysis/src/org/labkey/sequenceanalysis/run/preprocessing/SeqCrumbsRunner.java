package org.labkey.sequenceanalysis.run.preprocessing;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
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
 * Time: 7:45 PM
 */
public class SeqCrumbsRunner extends AbstractCommandWrapper
{
    public SeqCrumbsRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public File convertFormat(File input, File output) throws PipelineJobException
    {
        getLogger().info("Converting Files to FASTQ");
        getLogger().info("\tSeqCrumbs version: " + getVersion());

        execute(getParamsForConvertFormat(output, input));
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
        return SequencePipelineService.get().getExeForPackage("SEQCRUMBSPATH", "convert_format").getPath();
    }

    public String getVersion() throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add(getExePath());
        params.add("--version");

        String output = execute(params);
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