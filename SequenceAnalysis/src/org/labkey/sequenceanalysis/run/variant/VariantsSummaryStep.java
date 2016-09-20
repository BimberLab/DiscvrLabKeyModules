package org.labkey.sequenceanalysis.run.variant;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;

import java.io.File;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class VariantsSummaryStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public VariantsSummaryStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantsSummaryStep>
    {
        public Provider()
        {
            super("VariantsSummaryStep", "Generate Variant Summary", "", "This will generate various summary stats for the final VCF file", null, null, "");
        }

        public VariantsSummaryStep create(PipelineContext ctx)
        {
            return new VariantsSummaryStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome)
    {
        return null;
    }
}
