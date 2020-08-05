package org.labkey.sequenceanalysis.run.alignment;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 3/11/2015.
 */
public class FastqCollapser extends AbstractCommandWrapper
{
    public FastqCollapser(@Nullable Logger logger)
    {
        super(logger);
    }

    public File collapseFile(File input, File output) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("-i");
        args.add(input.getPath());
        args.add("-o");
        args.add(output.getPath());

        args.add("-Q");
        args.add("33");

        execute(args);

        if (!output.exists())
        {
            throw new PipelineJobException("expected file not created: " + output.getPath());
        }

        return output;
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("FASTXPATH", "fastx_collapser");
    }
}
