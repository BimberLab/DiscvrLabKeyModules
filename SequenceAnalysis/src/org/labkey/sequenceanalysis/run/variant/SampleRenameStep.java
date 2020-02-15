package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.VariantFiltrationWrapper;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 3/28/2017.
 */
public class SampleRenameStep extends AbstractCommandPipelineStep<VariantFiltrationWrapper> implements VariantProcessingStep
{
    public SampleRenameStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new VariantFiltrationWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SampleRenameStep> implements VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("SampleRenameStep", "Rename Samples", "GATK", "Rename samples in a VCF file", Arrays.asList(
                    ToolParameterDescriptor.create("sampleMap", "Sample Rename", "A list of the pairs of samples to rename.  It should contain two entries per line, the first being the original sample name and the second being the name to replace it.  They sould be separated by a comma.", "textarea", new JSONObject(){{
                        put("height", 200);
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.create("enforceChangeAll", "Change All Samples", "This is a check if you expect to change every sample in the VCF.  If the VCF contains a sample name, but no updated name is provided, the job will fail.", "checkbox", null, false)
                ), null, "");
        }

        public SampleRenameStep create(PipelineContext ctx)
        {
            return new SampleRenameStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        boolean enforceChangeAll = getProvider().getParameterByName("enforceChangeAll").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        String sampleMapString = getProvider().getParameterByName("sampleMap").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        Map<String, String> sampleMap = parseSampleMap(sampleMapString);

        File outputFile = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".renamed.vcf.gz");

        VariantContextWriterBuilder builder = new VariantContextWriterBuilder();
        builder.setReferenceDictionary(SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary()));
        builder.setOutputFile(outputFile);
        builder.setOption(Options.USE_ASYNC_IO);

        try (VCFFileReader reader = new VCFFileReader(inputVCF); VariantContextWriter writer = builder.build())
        {
            VCFHeader header = reader.getFileHeader();
            List<String> samples = header.getSampleNamesInOrder();
            List<String> remappedSamples = new ArrayList<>();

            int totalRenamed = 0;
            for (String sample : samples)
            {
                if (sampleMap.containsKey(sample))
                {
                    remappedSamples.add(sampleMap.get(sample));
                    totalRenamed++;
                }
                else if (enforceChangeAll)
                {
                    throw new PipelineJobException("No alternate name provided for sample: " + sample);
                }
                else
                {
                    remappedSamples.add(sample);
                }
            }

            getPipelineCtx().getLogger().info("renamed " + totalRenamed + " of " + samples.size() + " samples");
            if (remappedSamples.size() != samples.size())
            {
                throw new PipelineJobException("The number of renamed samples does not equal starting samples: " + samples.size() + " / " + remappedSamples.size());
            }

            writer.writeHeader(new VCFHeader(header.getMetaDataInInputOrder(), remappedSamples));

            List<Interval> queryIntervals = intervals;
            if (queryIntervals == null || queryIntervals.isEmpty())
            {
                queryIntervals.add(null);
            }

            int i = 0;
            for (Interval interval : queryIntervals)
            {
                try (CloseableIterator<VariantContext> it = (interval == null ? reader.iterator() : reader.query(interval.getContig(), interval.getStart(), interval.getEnd())))
                {
                    while (it.hasNext())
                    {
                        i++;
                        if (i % 100000 == 0)
                        {
                            getPipelineCtx().getLogger().info("processed " + i + " variants");
                        }

                        writer.add(it.next());
                    }
                }
            }
        }

        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");
        output.addOutput(outputFile, "Renamed VCF");
        output.setVcf(outputFile);

        return output;
    }

    private Map<String, String> parseSampleMap(String sampleMapString) throws PipelineJobException
    {
        sampleMapString = StringUtils.trimToNull(sampleMapString);
        if (sampleMapString == null)
        {
            throw new PipelineJobException("No sample map provided");

        }

        Map<String, String> ret = new HashMap<>();
        for (String line : sampleMapString.split("\\r?\\n"))
        {
            line = StringUtils.trimToNull(line);
            if (line == null)
            {
                continue;
            }

            String[] tokens = line.split(",");
            if (tokens.length != 2)
            {
                throw new PipelineJobException("Improper line: [" + line + "].  Each line should contain two IDs, separated by a comma");
            }

            ret.put(StringUtils.trimToNull(tokens[0]), StringUtils.trimToNull(tokens[1]));
        }

        return ret;
    }
}
