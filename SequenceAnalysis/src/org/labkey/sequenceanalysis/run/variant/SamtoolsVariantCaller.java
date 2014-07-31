package org.labkey.sequenceanalysis.run.variant;

import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AbstractVariantCallingStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.VariantCallingStep;

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
            super("SamtoolsVariantCaller", "Samtools/Pileup", "Call variants using samtools/pileup", null, null, "");
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
