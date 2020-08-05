package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 9/12/2014.
 */
public class FlagStatRunner extends SamtoolsRunner
{
    private static String COMMAND = "flagstat";

    public FlagStatRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public void execute(File input) throws PipelineJobException
    {
        getLogger().info("Generating BAM file stats: " + input.getPath());

        setLogLevel(Level.INFO);
        execute(getParams(input));
    }

    private List<String> getParams(File input)
    {
        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add(COMMAND);
        params.add(input.getPath());

        return params;
    }
}
