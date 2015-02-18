package org.labkey.sequenceanalysis.run.variant;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantCallingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.VariantCallingStep;

import java.io.File;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class SamtoolsVariantCaller extends AbstractPipelineStep implements VariantCallingStep
{
    public SamtoolsVariantCaller(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantCallingStepProvider<SamtoolsVariantCaller>
    {
        public Provider()
        {
            super("SamtoolsVariantCaller", "Samtools/Pileup", "Samtools", "Call variants using samtools/pileup", null, null, "");
        }

        public SamtoolsVariantCaller create(PipelineContext ctx)
        {
            return new SamtoolsVariantCaller(this, ctx);
        }
    }

    public PipelineStepOutput callVariants(File inputBam, File refFasta)
    {
        return null;
    }
}
