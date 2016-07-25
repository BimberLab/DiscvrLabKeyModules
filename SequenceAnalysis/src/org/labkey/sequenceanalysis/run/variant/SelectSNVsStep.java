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
public class SelectSNVsStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public SelectSNVsStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SelectSNVsStep>
    {
        public Provider()
        {
            super("SelectSNVs", "SelectSNVs", "GATK SelectVariants", "Select only SNVs from the input VCF", null, null, "");
        }

        public SelectSNVsStep create(PipelineContext ctx)
        {
            return new SelectSNVsStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, ReferenceGenome genome)
    {
        /**
         * 	$JAVA $JAVA_OPTS -jar $GATK \
         -R "$REF" \
         -T SelectVariants \
         -selectType SNP \
         -V "${FILTER2}" \
         -o "${SNV}"
         */
        return null;
    }
}
