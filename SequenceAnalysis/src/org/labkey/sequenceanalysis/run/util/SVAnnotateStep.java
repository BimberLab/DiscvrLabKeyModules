package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.util.Interval;
import org.apache.logging.log4j.Logger;
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
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SVAnnotateStep extends AbstractCommandPipelineStep<SVAnnotateStep.SNAnnotateWrapper> implements VariantProcessingStep
{
    public static final String GENE_PARAM = "gene_file";

    public SVAnnotateStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new SNAnnotateWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SVAnnotateStep>
    {
        public Provider()
        {
            super("SVAnnotateStep", "GATK SVAnnotate", "GATK", "This will run GATK's SVAnnotate to classify SVs by impact", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam(GENE_PARAM, "Gene File", "This is the ID of a GTF or GFF3 file containing genes from this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extensions", Arrays.asList("gtf"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null)
            ), PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js"), "");

        }

        @Override
        public SVAnnotateStep create(PipelineContext ctx)
        {
            return new SVAnnotateStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");

        List<String> args = new ArrayList<>(getWrapper().getBaseArgs("SVAnnotate"));
        args.add("-V");
        args.add(inputVCF.getPath());

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                args.add("-L");
                args.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        Integer geneFileId = getProvider().getParameterByName(GENE_PARAM).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File geneFile = getPipelineCtx().getSequenceSupport().getCachedData(geneFileId);
        if (!geneFile.exists())
        {
            throw new PipelineJobException("Unable to find file: " + geneFile.getPath());
        }
        args.add("--protein-coding-gtf");
        args.add(geneFile.getPath());

        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".svannotate.vcf.gz");
        getWrapper().execute(args);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("output not found: " + outputVcf);
        }

        output.setVcf(outputVcf);

        return output;
    }

    public static class SNAnnotateWrapper extends AbstractGatk4Wrapper
    {
        public SNAnnotateWrapper(@Nullable Logger logger)
        {
            super(logger);
        }
    }
}
