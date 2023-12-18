package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.util.Interval;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BcftoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BcftoolsFillTagsStep extends AbstractCommandPipelineStep<BcftoolsRunner> implements VariantProcessingStep
{
    public BcftoolsFillTagsStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new BcftoolsRunner(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<BcftoolsFillTagsStep> implements VariantProcessingStep.RequiresPedigree,  VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("BcftoolsFillTagsStep", "Bcftools Fill-tags", "bcftools", "Annotate variants using bcftools fill-tags", Arrays.asList(
                    ToolParameterDescriptor.create("hwe", "HWE", "If selected, HWE will be annotated", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.create("exchet", "Excess Het", "If selected, ExcHet will be annotated.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
            ), null, "");
        }

        @Override
        public BcftoolsFillTagsStep create(PipelineContext ctx)
        {
            return new BcftoolsFillTagsStep(this, ctx);
        }
    }

    @Override
    public VariantProcessingStep.Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> options = new ArrayList<>();
        options.add(getWrapper().getBcfToolsPath().getPath());
        options.add("+fill-tags");

        options.add(inputVCF.getPath());

        if (intervals != null)
        {
            options.add("--regions");
            options.add(intervals.stream().map(interval -> interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd()).collect(Collectors.joining(",")));
        }

        options.add("-O");
        options.add("z9");

        Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (threads != null)
        {
            options.add("--threads");
            options.add(threads.toString());
        }

        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".ft.vcf.gz");
        options.add("-o");
        options.add(outputVcf.getPath());

        List<String> annotations = new ArrayList<>();
        if (getProvider().getParameterByName("hwe").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false))
        {
            annotations.add("hwe");
        }

        if (getProvider().getParameterByName("exchet").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false))
        {
            annotations.add("exchet");
        }

        if (annotations.isEmpty())
        {
            throw new PipelineJobException("No annotations were selected");
        }

        options.add("-t");
        options.add(StringUtils.join(annotations, ","));

        BcftoolsRunner wrapper = getWrapper();

        String bcfPluginDir = StringUtils.trimToNull(System.getenv("BCFTOOLS_PLUGINS"));
        if (bcfPluginDir != null)
        {
            getPipelineCtx().getLogger().debug("Setting BCFTOOLS_PLUGINS environment variable: " + bcfPluginDir);
            wrapper.addToEnvironment("BCFTOOLS_PLUGINS", bcfPluginDir);
        }

        wrapper.execute(options);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("output not found: " + outputVcf);
        }

        try
        {
            SequenceAnalysisService.get().ensureVcfIndex(outputVcf, getWrapper().getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.setVcf(outputVcf);

        return output;
    }
}
