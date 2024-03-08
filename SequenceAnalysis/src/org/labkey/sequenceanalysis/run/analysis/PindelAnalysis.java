package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamPairUtil;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.SortingCollection;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFStandardHeaderLines;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.run.util.CollectInsertSizeMetricsWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;
import picard.analysis.InsertSizeMetrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class PindelAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public PindelAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<PindelAnalysis>
    {
        public Provider()
        {
            super("pindel", "Pindel Analysis", null, "This will run pindel on the selected BAMs.", Arrays.asList(
                    ToolParameterDescriptor.create("minFraction", "Min Fraction To Report", "Only variants representing at least this fraction of reads (based on depth at the start position) will be reported.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0.0);
                        put("maxValue", 1.0);
                        put("decimalPrecision", 2);
                    }}, 0.1),
                    ToolParameterDescriptor.create("minDepth", "Min Depth To Report", "Only variants representing at least this many reads (based on depth at the start position) will be reported.", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 10),
                    ToolParameterDescriptor.create("minInsertSize", "Min Insert Size", "Normally this tool will use the value of Picard CollectInsertSizeMetrics as the mean insert size to pass to pindel; however, this value can be used to set a minimum.", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 200),
                    ToolParameterDescriptor.create("writeToBamDir", "Write To BAM Dir", "If checked, outputs will be written to the BAM folder, as opposed to the output folder for this job.", "checkbox", new JSONObject(){{

                    }}, false),
                    ToolParameterDescriptor.create("removeDuplicates", "Remove Duplicates", "If checked, a temporatory BAM will be treated with reads marked as duplicates dropped.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
            ), null, null);
        }


        @Override
        public PindelAnalysis create(PipelineContext ctx)
        {
            return new PindelAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        boolean writeToBamDir = getProvider().getParameterByName("writeToBamDir").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        boolean removeDuplicates = getProvider().getParameterByName("removeDuplicates").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        Double minFraction = getProvider().getParameterByName("minFraction").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Double.class, 0.0);
        int minDepth = getProvider().getParameterByName("minDepth").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 0);
        int minInsertSize = getProvider().getParameterByName("minInsertSize").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 200);

        File gatkDepth = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + ".coverage");
        LofreqAnalysis.runDepthOfCoverage(getPipelineCtx(), output, outputDir, referenceGenome, inputBam, gatkDepth);

        File out = writeToBamDir ? inputBam.getParentFile() : outputDir;
        File summary = runPindel(output, getPipelineCtx(), rs, out, inputBam, referenceGenome.getWorkingFastaFile(), minFraction, minDepth, removeDuplicates, gatkDepth, minInsertSize);
        long lineCount = SequencePipelineService.get().getLineCount(summary) - 1;
        if (lineCount > 0)
        {
            output.addSequenceOutput(summary, rs.getName() + ": pindel", "Pindel Variants", rs.getReadsetId(), null, referenceGenome.getGenomeId(), "Total variants: " + lineCount);
        }
        else
        {
            getPipelineCtx().getLogger().info("No passing variants found");
        }

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    private static String parseInsertMetrics(File inputFile, int minInsertSize) throws IOException
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StringUtilsLabKey.DEFAULT_CHARSET)))
        {
            MetricsFile metricsFile = new MetricsFile();
            metricsFile.read(reader);
            List<InsertSizeMetrics> metrics = metricsFile.getMetrics();
            for (InsertSizeMetrics m : metrics)
            {
                if (m.PAIR_ORIENTATION == SamPairUtil.PairOrientation.FR)
                {
                    Double insertSize = Math.ceil(m.MEAN_INSERT_SIZE);

                    return String.valueOf(Math.max(minInsertSize, insertSize.intValue()));
                }
            }
        }

        return null;
    }

    private static String inferInsertSize(PipelineContext ctx, File bam, File fasta, int minInsertSize) throws PipelineJobException
    {
        File expectedPicard = new File(bam.getParentFile(), FileUtil.getBaseName(bam.getName()) + ".insertsize.metrics");
        if (!expectedPicard.exists())
        {
            ctx.getLogger().debug("Unable to find insert metrics file, creating: " + expectedPicard.getPath());
            CollectInsertSizeMetricsWrapper wrapper = new CollectInsertSizeMetricsWrapper(ctx.getLogger());
            File histFile = new File(expectedPicard.getPath() + ".hist");
            wrapper.executeCommand(bam, expectedPicard, histFile, fasta);
            histFile.delete();

            if (!expectedPicard.exists())
            {
                ctx.getLogger().error("CollectInsertSizeMetrics output not found, defaulting to " + minInsertSize + " as insert size");
                return(String.valueOf(minInsertSize));
            }
        }

        try
        {
            String ret = parseInsertMetrics(expectedPicard, minInsertSize);
            if (ret != null)
            {
                return ret;
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        ctx.getLogger().error("unable to infer insert size, defaulting to " + minInsertSize);

        return String.valueOf(minInsertSize);
    }

    public static File runPindel(AnalysisOutputImpl output, PipelineContext ctx, Readset rs, File outDir, File inputBam, File fasta, double minFraction, int minDepth, boolean removeDuplicates, File gatkDepth, int minInsertSize) throws PipelineJobException
    {
        File bamToUse = removeDuplicates ? new File(outDir, FileUtil.getBaseName(inputBam) + ".rmdup.bam") : inputBam;
        if (removeDuplicates)
        {
            File bamIdx = SequenceUtil.getExpectedIndex(bamToUse);
            if (!bamIdx.exists())
            {
                SamtoolsRunner runner = new SamtoolsRunner(ctx.getLogger());
                runner.execute(Arrays.asList(runner.getSamtoolsPath().getPath(), "rmdup", inputBam.getPath(), bamToUse.getPath()));
                runner.execute(Arrays.asList(runner.getSamtoolsPath().getPath(), "index", bamToUse.getPath()));
            }
            else
            {
                ctx.getLogger().debug("rmdup BAM already exists, reusing");
            }

            output.addIntermediateFile(bamToUse);
            output.addIntermediateFile(bamIdx);
        }

        File pindelParams = new File(outDir, "pindelCfg.txt");
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(pindelParams), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            String insertSize = inferInsertSize(ctx, inputBam, fasta, minInsertSize);
            writer.writeNext(new String[]{bamToUse.getPath(), insertSize, FileUtil.makeLegalName(rs.getName())});
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        SimpleScriptWrapper wrapper = new SimpleScriptWrapper(ctx.getLogger());

        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getExeForPackage("PINDELPATH", "pindel").getPath());
        args.add("-f");
        args.add(fasta.getPath());
        args.add("-i");
        args.add(pindelParams.getPath());
        args.add("-o");
        File outPrefix = new File(outDir, FileUtil.getBaseName(inputBam) + ".pindel");
        args.add(outPrefix.getPath());

        Integer threads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
        if (threads != null)
        {
            args.add("-T");
            args.add(threads.toString());
        }

        wrapper.execute(args);

        File outTsv = new File(outDir, FileUtil.getBaseName(inputBam) + ".pindel.txt");
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outTsv), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            writer.writeNext(new String[]{"Type", "Contig", "Start", "End", "Depth", "ReadSupport", "Fraction", "MeanFlankingCoverage", "LeadingCoverage", "TrailingCoverage", "EventCoverage", "Ref", "Alt", "PindelAllele"});
            parsePindelOutput(ctx, writer, new File(outPrefix.getPath() + "_D"), minFraction, minDepth, gatkDepth, fasta);
            parsePindelOutput(ctx, writer, new File(outPrefix.getPath() + "_SI"), minFraction, minDepth, gatkDepth, fasta);
            parsePindelOutput(ctx, writer, new File(outPrefix.getPath() + "_LI"), minFraction, minDepth, gatkDepth, fasta);
            parsePindelOutput(ctx, writer, new File(outPrefix.getPath() + "_INV"), minFraction, minDepth, gatkDepth, fasta);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return outTsv;
    }

    private static void parsePindelOutput(PipelineContext ctx, CSVWriter writer, File pindelFile, double minFraction, int minDepth, File gatkDepthFile, File fasta) throws IOException
    {
        ctx.getLogger().info("inspecting file: " + pindelFile.getName());

        if (!pindelFile.exists())
        {
            ctx.getLogger().debug("file does not exist: " + pindelFile.getPath());
            return;
        }

        int totalPassing = 0;
        int totalFiltered = 0;
        Map<String, ReferenceSequence> contigMap = new HashMap<>();
        try (BufferedReader reader = Readers.getReader(pindelFile);IndexedFastaSequenceFile iff = new IndexedFastaSequenceFile(fasta))
        {
            final int WINDOW_SIZE = 50;

            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.contains("Supports "))
                {
                    String[] tokens = line.split("\t");
                    int support = Integer.parseInt(tokens[8].split(" ")[1]);
                    if (support < minDepth)
                    {
                        totalFiltered++;
                        continue;
                    }

                    String contig = tokens[3].split(" ")[1];

                    // Example 26154-26158 (3 bp, reporting padded borders)
                    int basePriorToStart = Integer.parseInt(tokens[4].split(" ")[1]);

                    // Capture depth before/after event:
                    int depth = getGatkDepth(ctx, gatkDepthFile, contig, basePriorToStart);
                    if (depth == 0)
                    {
                        totalFiltered++;
                        continue;
                    }

                    int i = 0;
                    double leadingCoverage = 0.0;
                    while (i < WINDOW_SIZE) {
                        int pos = basePriorToStart - i;
                        if (pos < 1)
                        {
                            break;
                        }

                        leadingCoverage += getGatkDepth(ctx, gatkDepthFile, contig, pos);
                        i++;
                    }

                    leadingCoverage = leadingCoverage / i;

                    //NOTE: this is the indel region itself, no flanking. so for a deletion with REF/ALT of ATTC / A--C, it reports TT. for an insertion of ATT / AGTT, it reports G
                    String pindelAllele = tokens[2].split(" ")[2];
                    pindelAllele = StringUtils.trimToNull(pindelAllele.replaceAll("\"", ""));

                    File dict = new File(fasta.getPath().replace("fasta", "dict"));
                    if (!dict.exists())
                    {
                        throw new IOException("Unable to find file: "+ dict.getPath());
                    }

                    SAMSequenceDictionary extractor = SAMSequenceDictionaryExtractor.extractDictionary(dict.toPath());
                    SAMSequenceRecord rec = extractor.getSequence(contig);

                    String type = tokens[1].split(" ")[0];
                    int baseAfterEnd = Integer.parseInt(tokens[5]);
                    int trueEnd = baseAfterEnd - 1;

                    // Capture depth before/after event:
                    int j = 0;
                    double trailingCoverage = 0.0;
                    while (j < WINDOW_SIZE)
                    {
                        int pos = baseAfterEnd + j;
                        if (pos > rec.getSequenceLength())
                        {
                            break;
                        }

                        trailingCoverage += getGatkDepth(ctx, gatkDepthFile, contig, pos);
                        j++;
                    }

                    trailingCoverage = trailingCoverage / j;

                    Double eventCoverage = null;
                    if ("D".equals(type) || "INV".equals(type))
                    {
                        eventCoverage = 0.0;
                        int pos = basePriorToStart;
                        while (pos < baseAfterEnd)
                        {
                            pos++;
                            eventCoverage += getGatkDepth(ctx, gatkDepthFile, contig, pos);
                        }

                        eventCoverage = eventCoverage / (baseAfterEnd - basePriorToStart - 1);
                    }
                    else if ("I".equals(type))
                    {
                        eventCoverage = (double)depth;
                    }

                    double meanCoverage = (leadingCoverage + trailingCoverage) / 2.0;
                    double pct = (double)support / meanCoverage;
                    if (pct >= minFraction)
                    {
                        if (!contigMap.containsKey(contig))
                        {
                            contigMap.put(contig, iff.getSequence(contig));
                        }

                        ReferenceSequence sequence = contigMap.get(contig);
                        String alt = "";
                        String ref = "";
                        if ("I".equals(type))
                        {
                            if (pindelAllele == null)
                            {
                                throw new IllegalArgumentException("Unexpected empty allele for insertion: " + basePriorToStart);
                            }

                            ref = sequence.getBaseString().substring(basePriorToStart-1, basePriorToStart);
                            alt = ref + pindelAllele;
                        }
                        else if ("D".equals(type))
                        {
                            ref = sequence.getBaseString().substring(basePriorToStart-1, trueEnd);
                            alt = sequence.getBaseString().substring(basePriorToStart-1, basePriorToStart);
                            if (pindelAllele != null)
                            {
                                type = "S";
                                alt = alt + pindelAllele;
                            }
                        }

                        writer.writeNext(new String[]{type, contig, String.valueOf(basePriorToStart), String.valueOf(trueEnd), String.valueOf(depth), String.valueOf(support), String.valueOf(pct), String.valueOf(meanCoverage), String.valueOf(leadingCoverage), String.valueOf(trailingCoverage), (eventCoverage == null ? "" : String.valueOf(eventCoverage)), ref, alt, (pindelAllele == null ? "" : pindelAllele)});
                        totalPassing++;
                    }
                    else
                    {
                        totalFiltered++;
                    }
                }
            }

            ctx.getLogger().info("total filtered: " + totalFiltered);
            ctx.getLogger().info("total passing: " + totalPassing);
        }
    }

    private static int getGatkDepth(PipelineContext ctx, File gatkDepth, String contig, int position1) throws IOException
    {
        //skip header:
        int lineNo = 1 + position1;
        try (Stream<String> lines = Files.lines(gatkDepth.toPath()))
        {
            String[] line = lines.skip(lineNo - 1).findFirst().get().split("\t");

            if (!line[0].equals(contig + ":" + position1))
            {
                throw new IOException("Incorrect line at " + lineNo + ", expected " + contig + ":" + position1 + ", but was: " + line[0]);
            }

            return Integer.parseInt(line[1]);
        }
        catch (Exception e)
        {
            ctx.getLogger().error("Error parsing GATK depth: " + gatkDepth.getPath() + " / " + lineNo);
            throw new IOException(e);
        }
    }

    public static class PindelSettings
    {
        public int MAX_DEL_EVENT_COVERAGE = 20;
        public double MIN_AF = 0.25;
        public int MIN_LENGTH_TO_CONSIDER = 10;
        public int MAX_DELETION_LENGTH = 5000;
    }

    public static File createVcf(File pindelOutput, File vcfOutput, ReferenceGenome genome, PindelSettings settings) throws PipelineJobException
    {
        SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());

        VariantContextWriterBuilder b = new VariantContextWriterBuilder()
                .setOutputFile(vcfOutput)
                .setOption(Options.USE_ASYNC_IO)
                .setReferenceDictionary(dict);

        try (CSVReader reader = new CSVReader(Readers.getReader(pindelOutput), '\t'))
        {
            VCFHeader header = new VCFHeader();
            header.setSequenceDictionary(dict);
            LofreqAnalysis.addMetaLines(header);
            header.addMetaDataLine(VCFStandardHeaderLines.getInfoLine(VCFConstants.ALLELE_FREQUENCY_KEY));
            header.addMetaDataLine(VCFStandardHeaderLines.getInfoLine(VCFConstants.DEPTH_KEY));
            SortingCollection<VariantContext> toWrite = LofreqAnalysis.getVariantSorter(header);
            int totalAdded = 0;

            String[] line;
            while ((line = reader.readNext()) != null)
            {
                int inConsensus = 1;
                if (!("D".equals(line[0]) || "I".equals(line[0]) || "S".equals(line[0])))
                {
                    continue;
                }

                int start = Integer.parseInt(line[2]);  //1-based, coordinate prior, like VCF
                int end = Integer.parseInt(line[3]);  //1-based, actual coordinate, like VCF
                String refAllele = line[11];
                String altAllele = line[12];
                int refLength = end - start;
                int altLength = altAllele.length();

                // Assume LoFreq calls these well enough:
                if (refLength < settings.MIN_LENGTH_TO_CONSIDER && altLength < settings.MIN_LENGTH_TO_CONSIDER)
                {
                    inConsensus = 0;
                }

                if (("D".equals(line[0]) || "S".equals(line[0])) && refLength > settings.MAX_DELETION_LENGTH)
                {
                    inConsensus = 0;
                }

                if (Double.parseDouble(line[6]) < settings.MIN_AF)
                {
                    inConsensus = 0;
                }

                double eventCoverage = 0.0;
                if (StringUtils.trimToNull(line[10]) != null)
                {
                    eventCoverage = Double.parseDouble(line[10]);
                }

                if (("D".equals(line[0]) || "S".equals(line[0])) && eventCoverage > settings.MAX_DEL_EVENT_COVERAGE)
                {
                    inConsensus = 0;
                }

                VariantContextBuilder vcb = new VariantContextBuilder();
                vcb.start(start);
                vcb.stop(end);
                vcb.chr(line[1]);
                vcb.alleles(Arrays.asList(Allele.create(refAllele, true), Allele.create(altAllele)));
                vcb.attribute(VCFConstants.ALLELE_FREQUENCY_KEY, Double.parseDouble(line[6]));

                vcb.attribute("IN_CONSENSUS", inConsensus);
                vcb.attribute("GATK_DP", (int) Double.parseDouble(line[7]));

                int dp = "I".equals(line[0]) ? Integer.parseInt(line[4]) : (int) Double.parseDouble(line[10]);
                vcb.attribute(VCFConstants.DEPTH_KEY, dp);

                totalAdded++;
                toWrite.add(vcb.make());
            }

            if (totalAdded > 0)
            {
                try (CloseableIterator<VariantContext> iterator = toWrite.iterator(); VariantContextWriter writer = b.build())
                {
                    writer.writeHeader(header);
                    while (iterator.hasNext())
                    {
                        writer.add(iterator.next());
                    }
                }
            }

            toWrite.cleanup();
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return vcfOutput;
    }
}