package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GenomicsDbImportWrapper extends AbstractGatk4Wrapper
{
    public GenomicsDbImportWrapper(Logger log)
    {
        super(log);
    }

    public void execute(List<File> inputGvcfs, File outputFile, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running GATK 4 GenomicsDBImport");

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("GenomicsDBImport");

        inputGvcfs.forEach(f -> {
            args.add("-V");
            args.add(f.getPath());
        });

        args.add("--genomicsdb-workspace-path");
        args.add(outputFile.getPath());

        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);

        if (!outputFile.exists())
        {
            throw new PipelineJobException("Unable to find expected file: " + outputFile.getPath());
        }
    }
}
