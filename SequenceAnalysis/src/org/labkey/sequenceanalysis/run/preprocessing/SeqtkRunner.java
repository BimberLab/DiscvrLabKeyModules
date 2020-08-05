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

/**
 * User: bimber
 * Date: 11/20/12
 * Time: 7:45 PM
 */
public class SeqtkRunner extends AbstractCommandWrapper
{
    public SeqtkRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public File convertIlluminaToSanger(File input, File output) throws PipelineJobException
    {
        getLogger().info("Converting Illumina Encoded FASTQ to Sanger");

        List<String> params = new LinkedList<>();
        params.add(getExePath());
        params.add("seq");
        params.add("-VQ64");
        params.add(input.getPath());

        execute(params, output);
        if (!output.exists())
        {
            throw new PipelineJobException("No file created, expected: " + output.getPath());
        }

        return output;
    }

    private String getExePath()
    {
        return SequencePipelineService.get().getExeForPackage("SEQTKPATH", "seqtk").getPath();
    }
}