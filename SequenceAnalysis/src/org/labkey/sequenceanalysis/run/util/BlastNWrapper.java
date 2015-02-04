package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 2/3/2015.
 */
public class BlastNWrapper extends AbstractCommandWrapper
{
    public BlastNWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public void doRemoteBlast(File fasta, File output) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("-db");
        args.add("nr");
        args.add("-query");
        args.add(fasta.getPath());
        args.add("-remote");
        args.add("-out");
        args.add(output.getPath());

        execute(args);
    }

    public File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BLASTPATH", "blastn");
    }
}
