package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
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
public class SelectSamplesStep extends AbstractCommandPipelineStep<SelectVariantsWrapper> implements VariantProcessingStep
{
    public static String SAMPLE_INCLUDE = "sampleNameToInclude";
    public static String SAMPLE_EXCLUDE = "sampleNameToExclude";

    public SelectSamplesStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new SelectVariantsWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SelectSamplesStep> implements VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("SelectSamples", "Select Specific Samples", "GATK SelectVariants", "A VCF will be generated containing only the samples specified below.", Arrays.asList(
                ToolParameterDescriptor.create(SAMPLE_INCLUDE, "Select Sample(s) Include", "Only variants of the selected type(s) will be included", "sequenceanalysis-trimmingtextarea", null, null),
                ToolParameterDescriptor.create(SAMPLE_EXCLUDE, "Select Samples(s) To Exclude", "Variants of the selected type(s) will be excluded", "sequenceanalysis-trimmingtextarea", null, null)
            ), PageFlowUtil.set("/sequenceanalysis/field/TrimmingTextArea.js"), "https://software.broadinstitute.org/gatk/");
        }

        @Override
        public SelectSamplesStep create(PipelineContext ctx)
        {
            return new SelectSamplesStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();
        List<String> options = new ArrayList<>();

        String toInclude = getProvider().getParameterByName(SAMPLE_INCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        SelectVariantsStep.addSubjectSelectOptions(toInclude, options, "-sn");

        String toExclude = getProvider().getParameterByName(SAMPLE_EXCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        SelectVariantsStep.addSubjectSelectOptions(toExclude, options, "-xl-sn");

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                options.add("-L");
                options.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".selectSamples.vcf.gz");
        getWrapper().execute(genome.getWorkingFastaFile(), inputVCF, outputVcf, options);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("output not found: " + outputVcf);
        }

        output.setVcf(outputVcf);

        return output;
    }
}
