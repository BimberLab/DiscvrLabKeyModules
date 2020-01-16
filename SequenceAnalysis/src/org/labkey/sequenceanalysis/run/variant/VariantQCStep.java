package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;

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
public class VariantQCStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public VariantQCStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantQCStep> implements VariantProcessingStep.RequiresPedigree
    {
        public Provider()
        {
            super("VariantQCStep", "VariantQC", "", "This will generate an HTML summary report for the final VCF file", Arrays.asList(
                    ToolParameterDescriptor.create("writeJson", "Write Raw Data", "If selected, both an HTML report and a text file with the raw data will be created.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
            ), null, "https://bimberlab.github.io/DISCVRSeq/");
        }

        public VariantQCStep create(PipelineContext ctx)
        {
            return new VariantQCStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> options = new ArrayList<>();

        File pedFile = ProcessVariantsHandler.getPedigreeFile(getPipelineCtx().getSourceDirectory(true));
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
