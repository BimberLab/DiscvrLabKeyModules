package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/15/12
 * Time: 9:07 PM
 */
public class FastaIndexer extends SamtoolsRunner
{
    private static String _command = "faidx";

    public FastaIndexer(Logger logger)
    {
        _logger = logger;
    }

    public File execute(File input) throws PipelineJobException
    {
        _logger.info("Building index for FASTA: " + input.getPath());

        doExecute(getWorkingDir(input), getParams(input));
        File output = new File(input.getPath() + ".fai");
        if (!output.exists())
            throw new PipelineJobException("Index not created, expected: " + output.getPath());

        return output;
    }

    public List<String> getParams(File input)
    {
        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add(_command);
        params.add(input.getPath());
        return params;
    }
}
