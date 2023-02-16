package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import htsjdk.variant.variantcontext.VariantContext;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class SelectSNVsStep extends AbstractCommandPipelineStep<SelectVariantsWrapper> implements VariantProcessingStep
{
    public static String SELECT_TYPE_TO_INCLUDE = "selectType";
    public static String SELECT_TYPE_TO_EXCLUDE = "selectTypeToExclude";

    public SelectSNVsStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new SelectVariantsWrapper(ctx.getLogger()));
    }

    public static String getSelectTypes()
    {
        List<String> ret = new ArrayList<>();
        for (VariantContext.Type t : VariantContext.Type.values())
        {
            ret.add(t.name());
        }

        return StringUtils.join(ret, ";");
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SelectSNVsStep> implements VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("SelectSNVs", "Select Variants By Type", "GATK SelectVariants", "Select only variants of the desired type from the input VCF", Arrays.asList(
                ToolParameterDescriptor.create(SELECT_TYPE_TO_INCLUDE, "Select Type(s) To Include", "Only variants of the selected type(s) will be included", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", getSelectTypes());
                    put("multiSelect", true);
                }}, "SNV"),
                ToolParameterDescriptor.create(SELECT_TYPE_TO_EXCLUDE, "Select Type(s) To Exclude", "Variants of the selected type(s) will be excluded", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", getSelectTypes());
                    put("multiSelect", true);
                }}, null)
            ), PageFlowUtil.set("/ldk/field/SimpleCombo.js"), "https://software.broadinstitute.org/gatk/");
        }

        @Override
        public SelectSNVsStep create(PipelineContext ctx)
        {
            return new SelectSNVsStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> options = new ArrayList<>();

        String toInclude = getProvider().getParameterByName(SELECT_TYPE_TO_INCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        SelectVariantsStep.addSelectTypeOptions(toInclude, options, "--select-type-to-include");

        String toExclude = getProvider().getParameterByName(SELECT_TYPE_TO_EXCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        SelectVariantsStep.addSelectTypeOptions(toExclude, options, "--select-type-to-exclude");

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                options.add("-L");
                options.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".selectType.vcf.gz");
        getWrapper().execute(genome.getWorkingFastaFile(), inputVCF, outputVcf, options);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("output not found: " + outputVcf);
        }

        output.setVcf(outputVcf);

        return output;
    }
}
