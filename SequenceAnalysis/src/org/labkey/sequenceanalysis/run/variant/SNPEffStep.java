package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.apache.logging.log4j.Logger;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
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
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class SNPEffStep extends AbstractCommandPipelineStep<SnpEffWrapper> implements VariantProcessingStep
{
    public static final String GENE_PARAM = "gene_file";

    public SNPEffStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new SnpEffWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SNPEffStep> implements VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("SNPEffStep", "SNPEff", "SNPEff", "Annotate predicted functional effects using SNPEff", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam(GENE_PARAM, "Gene File", "This is the ID of a GTF or GFF3 file containing genes from this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
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

    private static final String NAME = "snpEff";

    public static File checkOrCreateIndex(SequenceAnalysisJobSupport support, Logger log, ReferenceGenome genome, Integer geneFileId) throws PipelineJobException
    {
        log.debug("checking for index");

        File snpEffBaseDir = AlignerIndexUtil.getIndexDir(genome, NAME);
        File geneFile = support.getCachedData(geneFileId);
        if (geneFile == null || !geneFile.exists())
        {
            throw new PipelineJobException("Unable to find file: " + geneFileId + "/" + geneFile);
        }

        SnpEffWrapper wrapper = new SnpEffWrapper(log);
        File snpEffIndexDir = wrapper.getExpectedIndexDir(snpEffBaseDir, genome.getGenomeId(), geneFileId);
        if (!snpEffIndexDir.exists())
        {
            wrapper.buildIndex(snpEffBaseDir, genome, geneFile, geneFileId);
        }
        else
        {
            log.debug("previously created index found, re-using: " + snpEffIndexDir.getPath());
        }

        return snpEffBaseDir;
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();
        getPipelineCtx().getLogger().info("Running SNPEff");

        Integer geneFileId = getProvider().getParameterByName(GENE_PARAM).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File snpEffBaseDir = checkOrCreateIndex(getPipelineCtx().getSequenceSupport(), getPipelineCtx().getLogger(), genome, geneFileId);

        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".snpEff.vcf.gz");
        if (outputVcf.exists())
        {
            getPipelineCtx().getLogger().debug("deleting pre-existing output file: " + outputVcf.getPath());
            outputVcf.delete();
        }

        File intFile = null;
        if (intervals != null)
        {
            intFile = new File(outputVcf.getParentFile(), "snpEffintervals.bed");
            try (PrintWriter writer = PrintWriters.getPrintWriter(intFile))
            {
                getPipelineCtx().getLogger().debug("Adding SnpEff intervals: " + intervals.size());
                intervals.forEach(interval -> {
                    writer.println(interval.getContig() + "\t" + (interval.getStart() - 1) + '\t' + interval.getEnd());
                });
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
