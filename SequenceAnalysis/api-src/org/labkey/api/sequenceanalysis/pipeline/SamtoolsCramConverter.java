package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 11/4/2016.
 */
public class SamtoolsCramConverter extends SamtoolsRunner
{
    public SamtoolsCramConverter(Logger log)
    {
        super(log);
    }

    public File convert(File inputBam, File outputCram, File gzippedFasta, boolean doIndex, @Nullable Integer threads) throws PipelineJobException
    {
        getLogger().info("Converting SAM/BAM to CRAM: " + inputBam.getPath());

        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add("view");

        params.add("-C");

        params.add("-o");
        params.add(outputCram.getPath());

        params.add("-T");
        params.add(gzippedFasta.getPath());

        if (threads != null)
        {
            params.add("--threads");
            params.add(String.valueOf(threads));
        }

        params.add(inputBam.getPath());

        execute(params);

        if (!outputCram.exists())
        {
            throw new PipelineJobException("Missing output: " + outputCram.getPath());
        }

        if (doIndex)
        {
            doIndex(outputCram, threads);
        }

        return outputCram;
    }

    public File doIndex(File input, @Nullable Integer threads) throws PipelineJobException
    {
        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add("index");

        if (threads != null)
        {
            params.add("-@");
            params.add(String.valueOf(threads));
        }

        params.add(input.getPath());
        execute(params);

        File idx = getExpectedCramIndex(input);
        if (!idx.exists())
        {
            throw new PipelineJobException("Unable to find CRAM index: " + idx.getPath());
        }

        return idx;
    }

    public static File getExpectedCramIndex(File input)
    {
        return new File(input.getPath() + ".crai");
    }
}
