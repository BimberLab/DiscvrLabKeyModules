package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;

import java.io.File;
import java.io.IOException;
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
        getLogger().info("Liftover VCF: " + inputVcf.getPath());

        List<String> params = getBaseArgs();
        params.add("INPUT=" + inputVcf.getPath());
        params.add("OUTPUT=" + outputVcf.getPath());
        params.add("CHAIN=" + chainFile.getPath());
        params.add("REFERENCE_SEQUENCE=" + referenceFasta.getPath());
        params.add("WRITE_ORIGINAL_POSITION=true");
        params.add("WRITE_ORIGINAL_ALLELES=true");
        params.add("LOG_FAILED_INTERVALS=false");
        params.add("RECOVER_SWAPPED_REF_ALT=false");
        params.add("LIFTOVER_MIN_MATCH=" + minPctMatch);
        if (rejectVcf != null)
            params.add("REJECT=" + rejectVcf.getPath());

        //See note in LiftoverVcf docs about this.  If the VCF has a lot of samples, a low number might be necessary
        params.add("MAX_RECORDS_IN_RAM=100000");

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

        return outputVcf;
    }

    @Override
    protected File getJar()
    {
        //NOTE: this has been added to use new features/arguments not yet in the release, and should be reverted once picard is updated
        File ret = super.getJar();
        if (ret != null)
        {
            ret = new File(ret.getParentFile(), "picard-liftover.jar");
        }

        return ret;
    }

    @Override
    protected String getToolName()
    {
        return "LiftoverVcf";
    }
}
