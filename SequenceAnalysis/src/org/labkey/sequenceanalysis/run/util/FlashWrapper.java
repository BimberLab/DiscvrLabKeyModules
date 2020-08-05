package org.labkey.sequenceanalysis.run.util;

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
 * Created by bimber on 2/3/2015.
 */
public class FlashWrapper extends AbstractCommandWrapper
{
    public FlashWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File execute(File fastqF, File fastqR, File outputDir, String outPrefix, @Nullable List<String> extraParams) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("-d");
        args.add(outputDir.getPath());
        args.add("-o");
        args.add(outPrefix);

        args.add("-z");

        if (extraParams != null)
        {
            args.addAll(extraParams);
        }

        args.add(fastqF.getPath());
        args.add(fastqR.getPath());

        execute(args);

        File output = new File(outputDir, outPrefix + ".extendedFrags.fastq.gz");
        if (!output.exists())
        {
            throw new PipelineJobException("Unable to find expected output: " + output.getPath());
        }

        return output;
    }

    public File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("FLASHPATH", "flash");
    }
}
