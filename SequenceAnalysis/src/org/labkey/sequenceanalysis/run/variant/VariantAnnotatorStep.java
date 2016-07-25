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
public class VariantAnnotatorStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public VariantAnnotatorStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantAnnotatorStep>
    {
        public Provider()
        {
            super("VariantAnnotatorStep", "GATK VariantAnnotator", "GATK", "Annotate variants using GATK VariantAnnotator", null, null, "");
        }

        public VariantAnnotatorStep create(PipelineContext ctx)
        {
            return new VariantAnnotatorStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, ReferenceGenome genome)
    {
        /**
         * #add MV count only after GenotypeFilters applied
         MV_ANN=${BASENAME}.annotated.filtered.mv.${SUFFIX}.vcf.gz
         if [ ! -e $MV_ANN ]; then
         $JAVA $JAVA_OPTS $TMP_OPTS -jar $GATK \
         -R "$REF" \
         -T VariantAnnotator \
         -o "$MV_ANN" \
         -nt 4 \
         -A MendelianViolationCount \
         -mvq 20 \
         -ped "$PED" \
         -pedValidationType SILENT \
         -V "$FILTER" \
         -I "$BAM_LIST" \
         --read_buffer_size 1000000

         /home/groups/prime-seq/pipeline_tools/bin/tabix -f $MV_ANN
         fi
         */

        /**
         * if [ ! -e $ANN ]; then
         $JAVA $JAVA_OPTS $TMP_OPTS -jar $GATK \
         -R "$REF" \
         -T VariantAnnotator \
         -o "$ANN" \
         -nt 4 \
         -A TandemRepeatAnnotator \
         -A ConflictingReadCount \
         -A ConflictingReadCountBySample \
         -ped "$PED" \
         -pedValidationType SILENT \
         -V "$INPUT" \
         -resource:eff "$SNPEFF_OUT" \
         -E eff.EFF \
         -resource:indian "$INDIAN_SUBSET" \
         -E indian.AF \
         -resource:chinese "$CHINESE_SUBSET" \
         -E chinese.AF \
         -I "$BAM_LIST" \
         --read_buffer_size 1000000

         /home/groups/prime-seq/pipeline_tools/bin/tabix -f $ANN
         fi
         */
        return null;
    }
}
