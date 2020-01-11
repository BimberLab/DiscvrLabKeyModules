package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class SNPEffStep extends AbstractCommandPipelineStep<SnpEffWrapper> implements VariantProcessingStep
{
    public SNPEffStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new SnpEffWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SNPEffStep>
    {
        public Provider()
        {
            super("SNPEffStep", "SNPEff", "SNPEff", "Annotate predicted functional effects using SNPEff", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam("gene_file", "Gene File", "This is the ID of a GTF or GFF3 file containing genes from this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extensions", Arrays.asList("gtf", "gff"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null)
                ), PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js"), "http://snpeff.sourceforge.net/index.html");
        }

        public SNPEffStep create(PipelineContext ctx)
        {
            return new SNPEffStep(this, ctx);
        }
    }

    private final String NAME = "snpEff";

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable Interval interval) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();
        getPipelineCtx().getLogger().info("Running SNPEff");

        getPipelineCtx().getLogger().debug("checking for index");

        File snpEffBaseDir = AlignerIndexUtil.getWebserverIndexDir(genome, NAME);
        Integer geneFileId = getProvider().getParameterByName("gene_file").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File geneFile = getPipelineCtx().getSequenceSupport().getCachedData(geneFileId);
        if (geneFile == null || !geneFile.exists())
        {
            throw new PipelineJobException("Unable to find file: " + geneFileId + "/" + geneFile);
        }

        File snpEffIndexDir = getWrapper().getExpectedIndexDir(snpEffBaseDir, genome.getGenomeId(), geneFileId);
        if (!snpEffIndexDir.exists())
        {
            getWrapper().buildIndex(snpEffBaseDir, genome, geneFile, geneFileId);
        }
        else
        {
            getPipelineCtx().getLogger().debug("previously created index found, re-using: " + snpEffIndexDir.getPath());
        }


        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".snpEff.vcf.gz");
        if (outputVcf.exists())
        {
            getPipelineCtx().getLogger().debug("deleting pre-existing output file: " + outputVcf.getPath());
            outputVcf.delete();
        }

        File intFile = null;
        if (interval != null)
        {
            intFile = new File(outputVcf.getParentFile(), "snpEffintervals." + interval.getContig() + ".bed");
            try (PrintWriter writer = PrintWriters.getPrintWriter(intFile))
            {
                writer.println(interval.getContig() + "\t" + (interval.getStart() - 1) + '\t' + interval.getEnd());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        getWrapper().runSnpEff(genome.getGenomeId(), geneFileId, snpEffBaseDir, inputVCF, outputVcf, intFile);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("Unable to find output: " + outputVcf.getPath());
        }

        if (intFile != null)
        {
            output.addIntermediateFile(intFile);
        }

        output.setVcf(outputVcf);

        return output;
    }
}
