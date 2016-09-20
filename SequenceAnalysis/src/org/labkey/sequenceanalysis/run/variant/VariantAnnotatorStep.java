package org.labkey.sequenceanalysis.run.variant;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.VariantAnnotatorWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class VariantAnnotatorStep extends AbstractCommandPipelineStep<VariantAnnotatorWrapper> implements VariantProcessingStep
{
    public VariantAnnotatorStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new VariantAnnotatorWrapper(ctx.getLogger()));
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
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> options = new ArrayList<>();

        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".annotated.vcf.gz");

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
         -I "$BAM_LIST"


        /**
         * if [ ! -e $ANN ]; then
         $JAVA $JAVA_OPTS $TMP_OPTS -jar $GATK \
         -R "$REF" \
         -T VariantAnnotator \
         -o "$ANN" \
         -nt 4 \
         -A TandemRepeatAnnotator \
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

        getWrapper().execute(genome.getWorkingFastaFile(), inputVCF, outputVcf, options);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("output not found: " + outputVcf);
        }

        output.setVcf(outputVcf);

        return output;
    }
}
