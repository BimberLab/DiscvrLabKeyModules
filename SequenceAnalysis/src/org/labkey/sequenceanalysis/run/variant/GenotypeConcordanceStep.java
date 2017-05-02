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
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.VariantAnnotatorWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by bimber on 4/26/2017.
 */
public class GenotypeConcordanceStep extends AbstractCommandPipelineStep<VariantAnnotatorWrapper> implements VariantProcessingStep
{
    public GenotypeConcordanceStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new VariantAnnotatorWrapper(ctx.getLogger()));
    }
    public static class Provider extends AbstractVariantProcessingStepProvider<GenotypeConcordanceStep> implements VariantProcessingStep.RequiresPedigree
    {
        public Provider()
        {
            super("GenotypeConcordanceStep", "Annotate Genotype Concordance", "GATK", "Annotate genotypes relative to a reference VCF using a custom GATK Annotator", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam("refVCF", "Reference VCF", "This VCF will be used as the reference to annotate genotypes in the input VCF.  Genotypes that differ from this VCF will be annotated (but not filtered).  Genotypes not called in either VCF are skipped.", "ldk-expdatafield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null)
            ), Arrays.asList("ldk/field/ExpDataField.js"), "");
        }

        public GenotypeConcordanceStep create(PipelineContext ctx)
        {
            return new GenotypeConcordanceStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> options = new ArrayList<>();

        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".annotated.vcf.gz");

        output.addInput(inputVCF, "Input VCF");

        options.add("-A");
        options.add("GenotypeConcordance");
        options.add("-A");
        options.add("GenotypeConcordanceBySite");

        Integer fileId = getProvider().getParameterByName("refVCF").extractValue(getPipelineCtx().getJob(), getProvider(), Integer.class);
        if (fileId == null)
        {
            throw new PipelineJobException("No reference VCF provided");
        }

        File refVCF = getPipelineCtx().getSequenceSupport().getCachedData(fileId);
        if (refVCF == null || !refVCF.exists())
        {
            throw new PipelineJobException("Reference VCF not found." + (refVCF == null ? "" : "  Path: " + refVCF.getPath()));
        }
        output.addInput(refVCF, "Reference VCF");

        options.add("-resource:GT_SOURCE");
        options.add(refVCF.getPath());

        Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (threads != null)
        {
            options.add("-nt");
            options.add(String.valueOf(Math.min(threads, 8)));
        }

        getWrapper().execute(genome.getWorkingFastaFile(), inputVCF, outputVcf, options);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("output not found: " + outputVcf);
        }

        output.setVcf(outputVcf);

        return output;
    }
}
