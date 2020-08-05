package org.labkey.sequenceanalysis.run.preprocessing;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
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
public class Sff2FastqRunner extends AbstractCommandWrapper
{
    public Sff2FastqRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public File convertFormat(File input, File output) throws PipelineJobException
    {
        getLogger().info("Converting SFF Files to FASTQ");

        execute(getParamsForConvertFormat(input, output));
        if (!output.exists())
        {
            throw new PipelineJobException("No file created, expected: " + output.getPath());
        }
        return output;
    }

    private List<String> getParamsForConvertFormat(File input, File output) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add(getExePath());
        params.add("-o");
        params.add(output.getPath());

        params.add(input.getPath());

        return params;
    }

    private String getExePath()
    {
        return SequencePipelineService.get().getExeForPackage("SFF2FASTQPATH", "sff2fastq").getPath();
    }
}