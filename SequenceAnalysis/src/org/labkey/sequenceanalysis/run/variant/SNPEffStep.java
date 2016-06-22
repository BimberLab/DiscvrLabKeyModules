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
public class SNPEffStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public SNPEffStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SNPEffStep>
    {
        public Provider()
        {
            super("SNPEffStep", "SNPEff", "SNPEff", "Annotate predicted functional effects using SNPEff", null, null, "");
        }

        public SNPEffStep create(PipelineContext ctx)
        {
            return new SNPEffStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, ReferenceGenome genome)
    {
        return null;
    }
}
