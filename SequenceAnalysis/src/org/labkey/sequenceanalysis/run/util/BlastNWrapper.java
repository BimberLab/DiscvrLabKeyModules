package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

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

    public File runBlastN(File dbDir, String blastDbGuid, File input, File outputFile, List<String> params) throws PipelineJobException
    {
        if (dbDir == null || !dbDir.exists())
        {
            throw new IllegalArgumentException("BLAST database dir does not exist: " + dbDir);
        }

        File db = new File(dbDir, blastDbGuid);

        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());

        args.add("-db");
        args.add(db.getPath());

        args.add("-query");
        args.add(input.getPath());

        args.add("-use_index");
        args.add("true");

        args.add("-index_name");
        args.add(db.getPath());

        args.add("-out");
        args.add(outputFile.getPath());

        if (params != null)
        {
            args.addAll(params);
        }

        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected file not created: " + outputFile.getPath());
        }

        return outputFile;
    }

    public void doRemoteBlast(File fasta, File output, @Nullable List<String> extraParams, @Nullable File blastdbDir) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("-db");
        args.add("nr");
        args.add("-query");
        args.add(fasta.getPath());
        args.add("-remote");

        if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
        {
            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
            if (maxThreads != null)
            {
                args.add("-num_threads");
                args.add(maxThreads.toString());
            }
        }

        args.add("-out");
        args.add(output.getPath());

        if (extraParams != null)
        {
            args.addAll(extraParams);
        }

        if (blastdbDir != null)
        {
            addToEnvironment("BLASTDB", blastdbDir.getPath());
        }

        execute(args);
    }

    public File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BLASTPATH", "blastn");
    }
}
