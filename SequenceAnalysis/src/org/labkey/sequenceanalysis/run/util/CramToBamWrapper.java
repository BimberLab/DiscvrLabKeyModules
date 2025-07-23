package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CramToBamWrapper extends SamtoolsRunner
{
    public CramToBamWrapper(Logger log)
    {
        super(log);
    }

    public void convert(File inputCram, File outputBam, File fasta, @Nullable Integer threads) throws PipelineJobException
    {
        getLogger().info("Converting CRAM to BAM");

        execute(getParams(inputCram, outputBam, fasta, threads));
    }

    private List<String> getParams(File inputCram, File outputBam, File fasta, @Nullable Integer threads)
    {
        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add("view");
        params.add("-b");
        params.add("-T");
        params.add(fasta.getPath());
        params.add("-o");
        params.add(outputBam.getPath());

        if (threads != null)
        {
            params.add("-@");
            params.add(String.valueOf(threads));
        }

        params.add(inputCram.getPath());

        return params;
    }
}
