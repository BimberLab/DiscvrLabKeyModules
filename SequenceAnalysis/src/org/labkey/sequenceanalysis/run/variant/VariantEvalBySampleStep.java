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
public class VariantEvalBySampleStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public VariantEvalBySampleStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantEvalBySampleStep>
    {
        public Provider()
        {
            super("VariantEvalBySampleStep", "GATK VariantEval By Sample", "GATK", "Generates a table of summary data from the final VCF.", null, null, "");
        }

        public VariantEvalBySampleStep create(PipelineContext ctx)
        {
            return new VariantEvalBySampleStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome)
    {
        return null;
    }
}
