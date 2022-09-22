package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 11/4/2016.
 */
public class SamtoolsIndexer extends SamtoolsRunner
{
    private static String COMMAND = "index";

    public SamtoolsIndexer(Logger log)
    {
        super(log);
    }

    public File execute(File inputBam) throws PipelineJobException
    {
        getLogger().info("Indexing SAM/BAM: " + inputBam.getPath());

        File idx = SequencePipelineService.get().getExpectedIndex(inputBam);
        if (idx.exists())
        {
            getLogger().debug("deleting existing index: " + idx.getPath());
            idx.delete();
        }

        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add(COMMAND);
        params.add(inputBam.getPath());

        execute(params);

        if (!idx.exists())
        {
            throw new PipelineJobException("Unable to find BAM index: " + idx.getPath());
        }

        return idx;
    }
}
