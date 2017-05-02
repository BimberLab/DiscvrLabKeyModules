package org.labkey.sequenceanalysis.run.variant;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.VariantAnnotatorWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantAnnotatorStep> implements VariantProcessingStep.RequiresPedigree
    {
        public Provider()
        {
            super("VariantAnnotatorStep", "GATK VariantAnnotator", "GATK", "Annotate variants using GATK VariantAnnotator", Arrays.asList(
                    ToolParameterDescriptor.create("mv", "Mendelian Violations", "If selected, mendelian violations will be annotated at the site and genotype level.  This requires records in the laboratory.subjects table matching the sample names of the VCF(s)", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, null),
                    ToolParameterDescriptor.create("maf", "Minor Allele Frequency", "If selected, MAF will be annotated.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, null)
            ), null, "");
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

        if (getProvider().getParameterByName("mv").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false))
        {
            options.add("-A");
            options.add("MendelianViolationCount");
            options.add("-A");
            options.add("MendelianViolationBySample");

            File pedFile = ProcessVariantsHandler.getPedigreeFile(getPipelineCtx().getSourceDirectory());
            if (!pedFile.exists())
            {
                throw new PipelineJobException("Unable to find pedigree file: " + pedFile.getPath());
            }

            options.add("-ped");
            options.add(pedFile.getPath());
            options.add("-pedValidationType");
            options.add("SILENT");
        }

        if (getProvider().getParameterByName("maf").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false))
        {
            options.add("-A");
            options.add("MinorAlleleFrequency");
        }

        Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (threads != null)
        {
            options.add("-nt");
            options.add(String.valueOf(Math.min(threads, 8)));
        }

         //TODO: allow annotation using fields from another VCF:
        /**
         -resource:indian "$INDIAN_SUBSET" \
         -E indian.AF \
         -resource:chinese "$CHINESE_SUBSET" \
         -E chinese.AF \
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
