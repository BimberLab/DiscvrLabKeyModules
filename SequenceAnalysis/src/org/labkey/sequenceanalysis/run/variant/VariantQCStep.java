package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PedigreeToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class VariantQCStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public VariantQCStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantQCStep> implements VariantProcessingStep.RequiresPedigree
    {
        public Provider()
        {
            super("VariantQCStep", "VariantQC", "", "This will generate an HTML summary report for the final VCF file", List.of(
                    ToolParameterDescriptor.create("writeJson", "Write Raw Data", "If selected, both an HTML report and a text file with the raw data will be created.", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.create("doCopyLocal", "Copy Input To Working Directory", "If selected, the input VCF will always be copied to the working directory, if it is not already present.", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    new PedigreeToolParameterDescriptor()
            ), PageFlowUtil.set(PedigreeToolParameterDescriptor.getClientDependencyPath()), "https://bimberlab.github.io/DISCVRSeq/");
        }

        @Override
        public VariantQCStep create(PipelineContext ctx)
        {
            return new VariantQCStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        boolean doCopyLocal = getProvider().getParameterByName("doCopyLocal").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), boolean.class, false);
        if (doCopyLocal)
        {
            File local = new File(outputDirectory, inputVCF.getName());
            File localIdx = new File(local.getPath() + ".tbi");
            if (!inputVCF.equals(local))
            {
                getPipelineCtx().getLogger().debug("Making local copy of VCF:");
                if (local.exists())
                {
                    local.delete();
                }

                if (localIdx.exists())
                {
                    localIdx.delete();
                }

                try
                {
                    FileUtils.copyFile(inputVCF, local);
                    FileUtils.copyFile(new File(inputVCF.getPath() + ".tbi"), localIdx);
                    output.addIntermediateFile(local);
                    output.addIntermediateFile(localIdx);
                    inputVCF = local;
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

        List<String> options = new ArrayList<>();

        String demographicsProviderName = getProvider().getParameterByName(PedigreeToolParameterDescriptor.NAME).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx());
        File pedFile = ProcessVariantsHandler.getPedigreeFile(getPipelineCtx().getSourceDirectory(true), demographicsProviderName);
        if (pedFile.exists())
        {
            options.add("-ped");
            options.add(pedFile.getPath());

            options.add("-pedValidationType");
            options.add("SILENT");
        }

        if (getProvider().getParameterByName("writeJson").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, true))
        {
            options.add("--rawData");
            options.add(new File(outputDirectory, SequencePipelineService.get().getUnzippedBaseName(inputVCF.getName()) + ".variantQC.json").getPath());
        }

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                options.add("-L");
                options.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (maxThreads != null)
        {
            options.add("--threads");
            options.add(String.valueOf(maxThreads));
        }

        File outputHtml = new File(outputDirectory, SequencePipelineService.get().getUnzippedBaseName(inputVCF.getName()) + ".variantQC.html");
        VariantQCWrapper wrapper = new VariantQCWrapper(getPipelineCtx().getLogger());
        wrapper.execute(inputVCF, genome.getWorkingFastaFile(), outputHtml, options);

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");
        output.addOutput(outputHtml, "VariantQC Report");

        output.addSequenceOutput(outputHtml, "VariantQC Report for: " + inputVCF.getName(), "VariantQC Report", null, null, genome.getGenomeId(), null);

        return output;
    }
}
