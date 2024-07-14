package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 3/24/2016.
 */
public class LiftoverBcfToolsWrapper extends PicardWrapper
{
    public LiftoverBcfToolsWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public void doLiftover(File inputVcf, File chainFile, File sourceGenomeFasta, File targetGenomeFasta, @Nullable File rejectVcf, File outputVcf) throws PipelineJobException
    {
        getLogger().info("Liftover VCF (bcftools): " + inputVcf.getPath());

        List<String> params = new ArrayList<>();
        params.add(SequencePipelineService.get().getExeForPackage("BCFTOOLS", "bcftools").getPath());
        params.add("+liftover");

        params.add("--no-version");
        params.add("-Oz");

        Integer threads = SequencePipelineService.get().getMaxThreads(getLogger());
        if (threads != null)
        {
            params.add("--threads");
            params.add(threads.toString());
        }

        params.add("-o");
        params.add(outputVcf.getPath());

        params.add(inputVcf.getPath());
        params.add("--");

        params.add("-s");
        params.add(sourceGenomeFasta.getPath());

        params.add("-f");
        params.add(targetGenomeFasta.getPath());

        params.add("-c");
        params.add(chainFile.getPath());

        params.add("--write-src");
        params.add("--fix-tags");

        if (rejectVcf != null)
        {
            params.add("--reject");
            params.add(rejectVcf.getPath());

            params.add("--reject-type");
            params.add("z");
        }

        execute(params);

        if (!outputVcf.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + outputVcf.getPath());
        }

        if (rejectVcf != null && rejectVcf.exists())
        {
            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(rejectVcf, getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    @Override
    protected String getToolName()
    {
        return "LiftoverVcf";
    }
}
