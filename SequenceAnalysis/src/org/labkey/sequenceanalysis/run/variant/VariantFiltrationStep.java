package org.labkey.sequenceanalysis.run.variant;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class VariantFiltrationStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public VariantFiltrationStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantFiltrationStep>
    {
        public Provider()
        {
            super("VariantFiltrationStep", "GATK VariantFiltration", "GATK", "Filter variants using GATK VariantFiltration", Arrays.asList(
                    ToolParameterDescriptor.create("filters", "Filters", "Filters that will be applied to the variants.", "sequenceanalysis-variantfilterfield", null, null)
            ), Arrays.asList("sequenceanalysis/field/VariantFilterField.js"), "");
        }

        public VariantFiltrationStep create(PipelineContext ctx)
        {
            return new VariantFiltrationStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, ReferenceGenome genome)
    {
        /**
         * 	$JAVA $JAVA_OPTS $TMP_OPTS -jar $GATK \
         -R "$REF" \
         -T VariantFiltration \
         -filterName "NoneCalled" \
         -filter "vc.getCalledChrCount() == 0" \
         -filterName "MendelianViolation" \
         -filter "vc.hasAttribute('MV_NUM') && MV_NUM > 0" \
         -V "${MV_ANN}" \
         -o "${FILTER2}" \
         --read_buffer_size 500000
         */

        /**
         * 	$JAVA $JAVA_OPTS $TMP_OPTS -jar $GATK \
         -R "$REF" \
         -T VariantFiltration \
         -filterName "QualityFilter" \
         -filter "vc.hasAttribute('QD') && QD < 5.0" \
         -filterName "FisherStrand" \
         -filter "FS > 15.0" \
         -filterName "MappingQuality" \
         -filter "MQ < 50.0" \
         -filterName "MQRankSum" \
         -filter "vc.hasAttribute('MQRankSum') && MQRankSum < -12.5" \
         -filterName "ReadPosRankSum" \
         -filter "vc.hasAttribute('ReadPosRankSum') && ReadPosRankSum < -8.0" \
         -window 10 \
         -cluster 3 \
         --maskName "RepeatMask" \
         --mask "$MASK" \
         -G_filter "DP < 10" \
         -G_filterName "DepthLT10"\
         -G_filter "DP > 100" \
         -G_filterName "DepthGT100"\
         -G_filter "GQ < 20" \
         -G_filterName "QualLT20"\
         --setFilteredGtToNocall \
         -V "${ANN}" \
         -o "${FILTER}" \
         --read_buffer_size 1000000
         */
        return null;
    }
}
