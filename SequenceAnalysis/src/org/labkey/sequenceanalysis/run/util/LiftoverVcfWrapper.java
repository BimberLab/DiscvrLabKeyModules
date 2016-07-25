package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by bimber on 3/24/2016.
 */
public class LiftoverVcfWrapper extends PicardWrapper
{
    public LiftoverVcfWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File doLiftover(File inputVcf, File chainFile, File referenceFasta, @Nullable File rejectVcf, File outputVcf, double minPctMatch) throws PipelineJobException
    {
        Date start = new Date();
        getLogger().info("Liftover VCF: " + inputVcf.getPath());

        List<String> params = new ArrayList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getPicardJar().getPath());
        params.add(getToolName());

        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        params.add("INPUT=" + inputVcf.getPath());
        params.add("OUTPUT=" + outputVcf.getPath());
        params.add("CHAIN=" + chainFile.getPath());
        params.add("REFERENCE_SEQUENCE=" + referenceFasta.getPath());
        params.add("WRITE_ORIGINAL_POSITION=true");
        params.add("LIFTOVER_MIN_MATCH=" + minPctMatch);
        if (rejectVcf != null)
            params.add("REJECT=" + rejectVcf.getPath());
        inferMaxRecordsInRam(params);

        execute(params);

        if (!outputVcf.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + outputVcf.getPath());
        }

        return outputVcf;
    }

    @Override
    protected String getToolName()
    {
        return "LiftoverVcf";
    }
}
