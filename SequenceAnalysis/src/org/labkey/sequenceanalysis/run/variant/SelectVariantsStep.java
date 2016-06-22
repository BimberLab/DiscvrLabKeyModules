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
public class SelectVariantsStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public SelectVariantsStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SelectVariantsStep>
    {
        public Provider()
        {
            super("SelectVariantsStep", "GATK SelectVariants", "GATK", "Select variants using GATK SelectVariants", null, null, "");
        }

        public SelectVariantsStep create(PipelineContext ctx)
        {
            return new SelectVariantsStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, ReferenceGenome genome)
    {
        return null;
    }
}
