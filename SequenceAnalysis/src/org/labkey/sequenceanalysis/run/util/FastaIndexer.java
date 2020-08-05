package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 12/15/12
 * Time: 9:07 PM
 */
public class FastaIndexer extends SamtoolsRunner
{
    private static String COMMAND = "faidx";

    public FastaIndexer(@Nullable Logger logger)
    {
        super(logger);
    }

    public File execute(File input) throws PipelineJobException
    {
        getLogger().info("Building index for FASTA: " + input.getPath());

        execute(getParams(input));
        File output = getExpectedIndexName(input);
        if (!output.exists())
            throw new PipelineJobException("Index not created, expected: " + output.getPath());

        return output;
    }

    public static File getExpectedIndexName(File input)
    {
        return new File(input.getPath() + ".fai");
    }

    public List<String> getParams(File input)
    {
        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add(COMMAND);
        params.add(input.getPath());
        return params;
    }
}
