package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalUtil;
import htsjdk.samtools.util.SortingCollection;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import htsjdk.variant.vcf.VCFRecordCodec;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.logging.log4j.Logger;
import org.biojava3.core.sequence.DNASequence;
import org.biojava3.core.sequence.compound.AmbiguityDNACompoundSet;
import org.biojava3.core.sequence.compound.NucleotideCompound;
import org.biojava3.core.sequence.io.DNASequenceCreator;
import org.biojava3.core.sequence.io.FastaReader;
import org.biojava3.core.sequence.io.GenericFastaHeaderParser;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.run.util.DepthOfCoverageWrapper;
import org.labkey.sequenceanalysis.run.variant.SNPEffStep;
import org.labkey.sequenceanalysis.run.variant.SnpEffWrapper;
import org.labkey.sequenceanalysis.util.ReferenceLibraryHelperImpl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static picard.sam.AbstractAlignmentMerger.MAX_RECORDS_IN_RAM;

public class LofreqAnalysis extends AbstractCommandPipelineStep<LofreqAnalysis.LofreqWrapper> implements AnalysisStep
{
    public static final String CATEGORY = "Lowfreq VCF";

    public LofreqAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new LofreqWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<LofreqAnalysis>
    {
        public Provider()
        {
            super("LofreqAnalysis", "LoFreq Analysis", null, "This will run LowFreq, a tool designed to call low-frequency mutations in a sample, such as viral populations or bacteria.  It is recommended to run GATK's BQSR and IndelRealigner upstream of this tool.", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam(SNPEffStep.GENE_PARAM, "Gene File", "This is the ID of a GTF or GFF3 file containing genes from this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extensions", Arrays.asList("gtf", "gff", "gbk"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.create("minCoverage", "Min Coverage For Consensus", "If provided, a consensus will only be called over regions with at least this depth", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 25),
                    ToolParameterDescriptor.createExpDataParam("primerBedFile", "Primer Sites (BED File)", "This is a BED file specifying the primer binding sites, which will be used to flag variants.  Strandedness is ignored.", "ldk-expdatafield", new JSONObject()
                    {{
                        put("allowBlank", true);
                    }}, null),
                    ToolParameterDescriptor.createExpDataParam("ampliconBedFile", "Amplicons (BED File)", "This is a BED file specifying the amplicons used for amplification (or any named regions of interest).  If provided, depth will be summarized across them.", "ldk-expdatafield", new JSONObject()
                    {{
                        put("allowBlank", true);
                    }}, null),
                    ToolParameterDescriptor.create("minFractionForConsensus", "Min AF For Consensus", "Any LoFreq variant greater than this threshold will be used as the consensus base.", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0.5);
                        put("maxValue", 1.0);
                        put("decimalPrecision", 2);
                    }}, 0.5),
                    ToolParameterDescriptor.create("minFraction", "Pindel Min Fraction To Report", "Only variants representing at least this fraction of reads (based on depth at the start position) will be reported.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0.0);
                        put("maxValue", 1.0);
                        put("decimalPrecision", 2);
                    }}, 0.1),
                    ToolParameterDescriptor.create("minDepth", "Pindel Min Depth To Report", "Only variants representing at least this many reads (based on depth at the start position) will be reported.", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 10),
                    ToolParameterDescriptor.create("minInsertSize", "Min Insert Size", "Normally this tool will use the value of Picard CollectInsertSizeMetrics as the mean insert size to pass to pindel; however, this value can be used to set a minimum.", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 200),
                    ToolParameterDescriptor.create("runPindel", "Run Pindel", "If selected, pindel will be used to identify large structural variants.", "checkbox", new JSONObject()
                    {{

                    }}, false),
                    ToolParameterDescriptor.create("runPangolin", "Run Pangolin and NextClade", "If selected, Pangolin and NextClade will be used to score the consensus against common SARS-CoV-2 lineages.", "checkbox", new JSONObject()
                    {{

                    }}, false),
                    ToolParameterDescriptor.create("strandBiasRecoveryAF", "Strand Bias Recovery AF", "LoFreq by default filters variants with strand bias; however, some library types can create these. If provided, any site filtered with sb_fdr that is above the provided AF will be unfiltered.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0.0);
                        put("maxValue", 1.0);
                        put("decimalPrecision", 2);
                    }}, 0.1),
                    ToolParameterDescriptor.create("dbImport", "Import SNVs", "If selected, the LoFreq and pindel variants will be imported into the DB.", "checkbox", new JSONObject()
                    {{

                    }}, false),
                    ToolParameterDescriptor.create("dbImportThreshold", "Variant Import AF Threshold", "If DB import is selected, variants above this AF threshold will be imported.", "ldk-numberfield", new JSONObject()
                    {{

                    }}, 0.1),
                    ToolParameterDescriptor.create("dbImportDepthThreshold", "Variant Import Depth Threshold", "If DB import is selected, variants in a site with coverage greater than or equal to this value will be imported.", "ldk-integerfield", new JSONObject()
                    {{

                    }}, 20)
            ), PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js"), "http://csb5.github.io/lofreq/");
        }


        @Override
        public LofreqAnalysis create(PipelineContext ctx)
        {
            return new LofreqAnalysis(this, ctx);
        }
    }

    public static void runDepthOfCoverage(PipelineContext ctx, AnalysisOutputImpl output, File outputDir, ReferenceGenome referenceGenome, File inputBam, File coverageOut) throws PipelineJobException
    {
        DepthOfCoverageWrapper wrapper = new DepthOfCoverageWrapper(ctx.getLogger());
        List<String> extraArgs = new ArrayList<>();
        extraArgs.add("--include-deletions");
        extraArgs.add("--omit-per-sample-statistics");
        extraArgs.add("--omit-interval-statistics");

        File intervalList = new File(outputDir, "depthOfCoverageIntervals.intervals");
        output.addIntermediateFile(intervalList);
        extraArgs.addAll(DepthOfCoverageWrapper.generateIntervalArgsForFullGenome(referenceGenome, intervalList));

        wrapper.run(Collections.singletonList(inputBam), coverageOut.getPath(), referenceGenome.getWorkingFastaFile(), extraArgs, true);
        if (!coverageOut.exists())
        {
            throw new PipelineJobException("Unable to find file: " + coverageOut.getPath());
        }
    }

    public static void addMetaLines(VCFHeader header)
    {
        header.addMetaDataLine(new VCFInfoHeaderLine("IN_CONSENSUS", 1, VCFHeaderLineType.Flag, "A flag to indicate whether this variant appears in the consensus"));
        header.addMetaDataLine(new VCFInfoHeaderLine("WITHIN_PBS", 1, VCFHeaderLineType.Flag, "A flag to indicate whether this variant is located in primer binding sites"));
        header.addMetaDataLine(new VCFInfoHeaderLine("GATK_DP", 1, VCFHeaderLineType.Integer, "The depth of coverage provided by GATK DepthOfCoverage"));
        header.addMetaDataLine(new VCFInfoHeaderLine("SB_RECOVER", 1, VCFHeaderLineType.Flag, "Indicates this variant was strand bias filtered by LoFreq, but was recovered"));
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        File outputVcfRaw = new File(outputDir, FileUtil.getBaseName(inputBam) + ".lofreq.vcf.gz");
        File outputVcfFiltered = new File(outputDir, FileUtil.getBaseName(inputBam) + ".lofreq.filtered.vcf.gz");
        File outputVcfSnpEff = new File(outputDir, FileUtil.getBaseName(inputBam) + ".lofreq.snpeff.vcf.gz");

        //LoFreq call
        getWrapper().execute(inputBam, outputVcfRaw, referenceGenome.getWorkingFastaFile(), SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger()));

        //LoFreq filter
        getWrapper().executeFilter(outputVcfRaw, outputVcfFiltered);
        output.addIntermediateFile(outputVcfRaw);
        output.addIntermediateFile(new File(outputVcfRaw.getPath() + ".tbi"));

        //Add depth for downstream use:
        File coverageOut = new File(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(outputVcfRaw.getName()) + ".coverage");
        runDepthOfCoverage(getPipelineCtx(), output, outputDir, referenceGenome, inputBam, coverageOut);

        //Create a BED file with all regions of coverage below MIN_COVERAGE:
        int minCoverage = getProvider().getParameterByName("minCoverage").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        int positionsSkipped = 0;
        int basesRecoveredFromDeletions = 0;
        int gapIntervals = 0;
        double avgDepth;

        boolean runPindel = getProvider().getParameterByName("runPindel").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);

        List<VariantContext> pindelConsensusVariants = new ArrayList<>();
        int totalPindelConsensusVariants = 0;
        int totalPindelVariants = 0;
        if (runPindel)
        {
            Double minFraction = getProvider().getParameterByName("minFraction").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Double.class, 0.0);
            int minDepth = getProvider().getParameterByName("minDepth").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 0);
            int minInsertSize = getProvider().getParameterByName("minInsertSize").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 0);

            PindelAnalysis.PindelSettings settings = new PindelAnalysis.PindelSettings();

            File pindelOutput = PindelAnalysis.runPindel(output, getPipelineCtx(), rs, outputDir, inputBam, referenceGenome.getWorkingFastaFile(), minFraction, minDepth, true, coverageOut, minInsertSize);
            File pindelVcf = PindelAnalysis.createVcf(pindelOutput, new File(pindelOutput.getParentFile(), FileUtil.getBaseName(pindelOutput) + ".all.vcf.gz"), referenceGenome, settings);
            if (pindelVcf.exists())
            {
                try (VCFFileReader reader = new VCFFileReader(pindelVcf); CloseableIterator<VariantContext> it = reader.iterator())
                {
                    while (it.hasNext())
                    {
                        VariantContext vc = it.next();
                        if (vc.hasAttribute("IN_CONSENSUS"))
                        {
                            pindelConsensusVariants.add(vc);
                            totalPindelConsensusVariants++;
                        }

                        totalPindelVariants++;
                    }
                }
            }

            getPipelineCtx().getLogger().info("Total pindel variants: " + totalPindelVariants);
            getPipelineCtx().getLogger().info("Total consensus pindel variants: " + totalPindelConsensusVariants);
            if (totalPindelConsensusVariants == 0 && pindelVcf.exists())
            {
                getPipelineCtx().getLogger().info("deleting empty pindel VCF: " + pindelVcf.getPath());
                pindelVcf.delete();
                new File(pindelVcf.getPath() + ".tbi").delete();
            }
        }

        File mask = new File(outputDir, "mask.bed");
        Map<String, Integer> gatkDepth = new HashMap<>();
        try (CSVReader reader = new CSVReader(Readers.getReader(coverageOut), '\t');CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedUtf8Writing(mask), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            String[] line;

            Interval intervalOfCurrentGap = null;
            double totalDepth = 0;
            double totalPositions = 0;

            int i = 0;
            while ((line = reader.readNext()) != null)
            {
                i++;
                if (i == 1)
                {
                    continue;
                }

                String[] tokens = line[0].split(":");
                int depth = Integer.parseInt(line[1]);
                gatkDepth.put(line[0], depth);

                totalPositions++;
                totalDepth += depth;

                if (depth < minCoverage)
                {
                    //Check if within pindel SNV calls:
                    Interval currentPosition = new Interval(tokens[0], Integer.parseInt(tokens[1]), Integer.parseInt(tokens[1]));
                    boolean withinDeletion = false;
                    for (VariantContext vc : pindelConsensusVariants)
                    {
                        if (vc.isIndel())
                        {
                            // If we overlap the SNV (which should be just deletions (note: the first position of the variant is the non-indel REF base), allow low coverage:
                            if (currentPosition.overlaps(vc) && currentPosition.getStart() != vc.getStart())
                            {
                                basesRecoveredFromDeletions++;

                                //TODO: enable this once sure:
                                //withinDeletion = true;
                                break;
                            }
                        }
                    }

                    if (!withinDeletion)
                    {
                        positionsSkipped++;

                        if (intervalOfCurrentGap != null)
                        {
                            if (intervalOfCurrentGap.getContig().equals(tokens[0]))
                            {
                                //extend
                                intervalOfCurrentGap = new Interval(intervalOfCurrentGap.getContig(), intervalOfCurrentGap.getStart(), Integer.parseInt(tokens[1]));
                            }
                            else
                            {
                                //switched contigs, write and make new:
                                writer.writeNext(new String[]{intervalOfCurrentGap.getContig(), String.valueOf(intervalOfCurrentGap.getStart() - 1), String.valueOf(intervalOfCurrentGap.getEnd())});
                                gapIntervals++;
                                intervalOfCurrentGap = currentPosition;
                            }
                        }
                        else
                        {
                            //Not existing gap, just start one:
                            intervalOfCurrentGap = currentPosition;
                        }

                        continue;
                    }
                }

                //Otherwise this is a valid position to report:
                //We just exited a gap, so write:
                if (intervalOfCurrentGap != null)
                {
                    writer.writeNext(new String[]{intervalOfCurrentGap.getContig(), String.valueOf(intervalOfCurrentGap.getStart()-1), String.valueOf(intervalOfCurrentGap.getEnd())});
                    gapIntervals++;
                }

                intervalOfCurrentGap = null;
            }

            //Ensure we count final gap
            if (intervalOfCurrentGap != null)
            {
                writer.writeNext(new String[]{intervalOfCurrentGap.getContig(), String.valueOf(intervalOfCurrentGap.getStart()-1), String.valueOf(intervalOfCurrentGap.getEnd())});
                gapIntervals++;
            }

            avgDepth = totalDepth / totalPositions;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        //Optional: amplicons/depth:
        NumberFormat decimal = DecimalFormat.getNumberInstance();
        decimal.setGroupingUsed(false);
        decimal.setMaximumFractionDigits(2);
        Integer ampliconBedId = getProvider().getParameterByName("ampliconBedFile").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        if (ampliconBedId != null)
        {
            File ampliconDepths = new File(outputDir, "ampliconDepths.txt");
            try (CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedUtf8Writing(ampliconDepths), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"AmpliconName", "Length", "TotalDepth", "AvgDepth", "MedianDepth"});
                File primerFile = getPipelineCtx().getSequenceSupport().getCachedData(ampliconBedId);
                try (CSVReader reader = new CSVReader(Readers.getReader(primerFile), '\t'))
                {
                    String[] line;
                    while ((line = reader.readNext()) != null)
                    {
                        if (line[0].startsWith("#"))
                        {
                            continue;
                        }

                        String contig = line[0];
                        int start = Integer.parseInt(line[1]) + 1;
                        int end = Integer.parseInt(line[2]);
                        String name = line[3];

                        int totalDepth = 0;
                        List<Double> depths = new ArrayList<>();
                        for (int i = start;i<=end;i++)
                        {
                            String key = contig + ":" + i;
                            if (!gatkDepth.containsKey(key))
                            {
                                getPipelineCtx().getLogger().error("Missing depth: " + key);
                                continue;
                            }

                            totalDepth += gatkDepth.get(key);
                            depths.add(gatkDepth.get(key).doubleValue());
                        }

                        int length = (end - start + 1);
                        Double avg = (double)totalDepth / length;
                        Median median = new Median();
                        double medianValue = median.evaluate(ArrayUtils.toPrimitive(depths.toArray(new Double[0])));
                        writer.writeNext(new String[]{name, String.valueOf(length), String.valueOf(totalDepth), decimal.format(avg), decimal.format(medianValue)});
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            getPipelineCtx().getLogger().info("No amplicon BED provided, skipping");
        }

        //SnpEff:
        Integer geneFileId = getProvider().getParameterByName(SNPEffStep.GENE_PARAM).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File snpEffBaseDir = SNPEffStep.checkOrCreateIndex(getPipelineCtx().getSequenceSupport(), getPipelineCtx().getLogger(), referenceGenome, geneFileId);

        SnpEffWrapper snpEffWrapper = new SnpEffWrapper(getPipelineCtx().getLogger());
        snpEffWrapper.runSnpEff(referenceGenome.getGenomeId(), geneFileId, snpEffBaseDir, outputVcfFiltered, outputVcfSnpEff, null);

        try
        {
            SequenceAnalysisService.get().ensureVcfIndex(outputVcfSnpEff, getPipelineCtx().getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.addIntermediateFile(outputVcfFiltered);
        output.addIntermediateFile(new File(outputVcfFiltered.getPath() + ".tbi"));

        double minFractionForConsensus = getProvider().getParameterByName("minFractionForConsensus").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Double.class, 0.0);

        Integer primerDataId = getProvider().getParameterByName("primerBedFile").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        List<Interval> primerIntervals = new ArrayList<>();
        if (primerDataId != null)
        {
            File primerFile = getPipelineCtx().getSequenceSupport().getCachedData(primerDataId);
            try (CSVReader reader = new CSVReader(Readers.getReader(primerFile), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    if (line[0].startsWith("#"))
                    {
                        continue;
                    }

                    primerIntervals.add(new Interval(line[0], Integer.parseInt(line[1]) + 1, Integer.parseInt(line[2])));
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            getPipelineCtx().getLogger().info("No primer BED provided, skipping");
        }

        //generate bcftools consensus
        Set<String> variantsBcftools = runBcftools(inputBam, referenceGenome, mask, minCoverage);
        int variantsBcftoolsTotal = variantsBcftools.size();

        //Create final VCF:
        int totalVariants = 0;
        int totalGT1 = 0;
        int totalGT50 = 0;
        int totalGTThreshold = 0;
        int filteredVariantsRecovered = 0;
        int consensusFilteredVariantsRecovered = 0;

        int totalIndelGT1 = 0;
        int totalIndelGTThreshold = 0;
        int totalConsensusInPBS= 0;

        File loFreqConsensusVcf = getConsensusVcf(outputDir, inputBam);
        File loFreqAllVcf = getAllVcf(outputDir, inputBam);
        Double strandBiasRecoveryAF = getProvider().getParameterByName("strandBiasRecoveryAF").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Double.class, 1.0);
        SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(referenceGenome.getSequenceDictionary().toPath());
        VariantContextWriterBuilder writerBuilderConsensus = new VariantContextWriterBuilder().setOutputFile(loFreqConsensusVcf).setReferenceDictionary(dict);
        VariantContextWriterBuilder writerBuilderAll = new VariantContextWriterBuilder().setOutputFile(loFreqAllVcf).setReferenceDictionary(dict);
        try (VCFFileReader reader = new VCFFileReader(outputVcfSnpEff);CloseableIterator<VariantContext> it = reader.iterator();VariantContextWriter writerConsensus = writerBuilderConsensus.build();VariantContextWriter writerAll = writerBuilderAll.build())
        {
            VCFHeader header = reader.getFileHeader();

            //Add INFO annotations
            addMetaLines(header);

            header.setSequenceDictionary(dict);
            writerConsensus.writeHeader(header);
            writerAll.writeHeader(header);

            SortingCollection<VariantContext> allVariants = getVariantSorter(header);
            SortingCollection<VariantContext> consensusVariants = getVariantSorter(header);
            while (it.hasNext())
            {
                VariantContext vc = it.next();
                VariantContextBuilder vcb = new VariantContextBuilder(vc);

                int gDepth = gatkDepth.get(vc.getContig() + ":" + vc.getStart());
                vcb.attribute("GATK_DP", gDepth);

                if (vc.isFiltered())
                {
                    //NOTE: LoFreq's strand bias filtering
                    Set<String> filters = vcb.getFilters();
                    if (filters.contains("sb_fdr"))
                    {
                        if (vc.hasAttribute("AF") && vc.getAttributeAsDouble("AF", 0.0) > strandBiasRecoveryAF)
                        {
                            filteredVariantsRecovered++;
                            filters = new LinkedHashSet<>(filters);
                            filters.remove("sb_fdr");
                            vcb.filters(filters);
                            vcb.attribute("SB_RECOVER", 1);

                            if (gDepth >= minCoverage && vc.hasAttribute("AF") && vc.getAttributeAsDouble("AF", 0.0) >= minFractionForConsensus)
                            {
                                consensusFilteredVariantsRecovered++;
                            }
                        }
                    }

                    //If still filtered, print and continue
                    if (vcb.getFilters() != null && !vcb.getFilters().isEmpty())
                    {
                        writerAll.add(vcb.make());
                        continue;
                    }
                }

                totalVariants++;
                if (vc.hasAttribute("AF") && vc.getAttributeAsDouble("AF", 0.0) > 0.01)
                {
                    totalGT1++;
                    if (vc.hasAttribute("INDEL"))
                    {
                        totalIndelGT1++;
                    }
                }

                if (vc.hasAttribute("AF") && vc.getAttributeAsDouble("AF", 0.0) > 0.5)
                {
                    totalGT50++;
                }

                boolean withinPrimer = false;
                if (!primerIntervals.isEmpty())
                {
                    for (Interval i : primerIntervals)
                    {
                        if (IntervalUtil.contains(i, vc.getContig(), vc.getStart()))
                        {
                            withinPrimer = true;
                            break;
                        }
                    }

                    if (withinPrimer)
                    {
                        vcb.attribute("WITHIN_PBS", 1);
                    }
                }

                if (gDepth >= minCoverage && vc.hasAttribute("AF") && vc.getAttributeAsDouble("AF", 0.0) >= minFractionForConsensus)
                {
                    totalGTThreshold++;

                    if (withinPrimer)
                    {
                        totalConsensusInPBS++;
                    }

                    //Note: if there are multiple variants, the sum might argue for consensus: i.e. G->T 0.43, G->C 0.23. wild-type is the minority
                    vcb.attribute("IN_CONSENSUS", 1);

                    if (vc.hasAttribute("INDEL"))
                    {
                        totalIndelGTThreshold++;
                    }

                    String key = getHashKey(vc);
                    if (!variantsBcftools.contains(key))
                    {
                        getPipelineCtx().getLogger().warn("The following variants were GT50% in lofreq, but not in bcftools: " + key + ", AF:" + vc.getAttribute("AF") + "/" + "DP:" + vc.getAttribute("DP"));
                    }
                    else
                    {
                        variantsBcftools.remove(key);
                    }

                    vc = vcb.make();
                    writerConsensus.add(vc);
                    writerAll.add(vc);
                }
                else
                {
                    if (gDepth < minCoverage && vc.getAttributeAsDouble("DP", 0.0) >= minCoverage)
                    {
                        getPipelineCtx().getLogger().error("The following variant was excluded from the consensus b/c of GATK depth, but DP is above that threshold: " + getHashKey(vc) + ", AF:" + vc.getAttribute("AF") + "/" + "DP:" + vc.getAttribute("DP") + "/GATK_DP:" + gDepth);
                    }

                    writerAll.add(vcb.make());
                }
            }

            if (!pindelConsensusVariants.isEmpty())
            {
                //TODO: enable once sure:
                //pindelConsensusVariants.stream().forEach(consensusVariants::add);
                getPipelineCtx().getLogger().info("total consensus variants that would be added: " + pindelConsensusVariants.size());
            }

            try (CloseableIterator<VariantContext> iterator = allVariants.iterator())
            {
                while (iterator.hasNext())
                {
                    writerAll.add(iterator.next());
                }
            }
            allVariants.cleanup();


            try (CloseableIterator<VariantContext> iterator = consensusVariants.iterator())
            {
                while (iterator.hasNext())
                {
                    writerConsensus.add(iterator.next());
                }
            }
            consensusVariants.cleanup();
        }

        NumberFormat fmt = NumberFormat.getPercentInstance();
        fmt.setMaximumFractionDigits(2);

        double pctNoCover = positionsSkipped / (double)dict.getReferenceLength();
        getPipelineCtx().getLogger().info("Total positions with coverage below threshold (" + minCoverage + "): " + positionsSkipped + "(" + fmt.format(pctNoCover) + ")");
        getPipelineCtx().getLogger().info("Total # gap intervals: " + gapIntervals);
        getPipelineCtx().getLogger().info("Total # of low coverage bases recovered within consensus deletions: " + basesRecoveredFromDeletions);

        String description = String.format("Total Variants: %s\nTotal GT 1 PCT: %s\nTotal GT 50 PCT: %s\nTotal Indel GT 1 PCT: %s\nPositions Below Coverage: %s\nTotal In LoFreq Consensus: %s\nTotal Indel In LoFreq Consensus: %s\nTotal Consensus Variant in PBS: %s", totalVariants, totalGT1, totalGT50, totalIndelGT1, positionsSkipped, totalGTThreshold, totalIndelGTThreshold, totalConsensusInPBS);
        description += "\n" + "Strand Bias Recovered: " + filteredVariantsRecovered;
        description += "\n" + "Consensus Strand Bias Recovered: " + consensusFilteredVariantsRecovered;
        if (totalPindelConsensusVariants > 0)
        {
            description += "\nPindel consensus: " + totalPindelConsensusVariants;
        }

        if (!variantsBcftools.isEmpty())
        {
            getPipelineCtx().getLogger().error("The following variants were called in bcftools, but not LoFreq consensus: " + StringUtils.join(variantsBcftools, ", "));
            description += "\n" + "WARNING: " + variantsBcftools.size() + " variants detected in bcftools but not lofreq consensus";
        }

        File consensusFastaBcfTools = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam.getName()) + ".bcftools.consensus.fasta");
        if (!consensusFastaBcfTools.exists())
        {
            throw new PipelineJobException("Expected file not found: " + consensusFastaBcfTools.getPath());
        }

        int bcfToolsConsensusNs = replaceFastHeader(consensusFastaBcfTools, rs, "bcftools|Variants:" + variantsBcftoolsTotal);

        //Make consensus using lofreq:
        File consensusFastaLoFreq = generateConsensus(loFreqConsensusVcf, referenceGenome.getWorkingFastaFile(), mask);
        int lofreqConsensusNs = replaceFastHeader(consensusFastaLoFreq, rs, "Lofreq|Variants:" + totalGTThreshold);
        description += "\nConsensus Ns: " + lofreqConsensusNs;

        if (lofreqConsensusNs < positionsSkipped)
        {
            getPipelineCtx().getLogger().error("Problem with masking of the genome. Insufficient non-covered positions");
        }


        if (bcfToolsConsensusNs != lofreqConsensusNs)
        {
            getPipelineCtx().getLogger().warn("Consensus ambiguities from bcftools and lofreq did not match: " + bcfToolsConsensusNs + " / " + lofreqConsensusNs);
        }

        output.addIntermediateFile(outputVcfSnpEff);
        output.addIntermediateFile(new File(outputVcfSnpEff.getPath() + ".tbi"));
        output.addSequenceOutput(coverageOut, "Depth of Coverage: " + rs.getName(), "Depth of Coverage", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);
        output.addSequenceOutput(consensusFastaLoFreq, "Consensus: " + rs.getName(), "Viral Consensus Sequence", rs.getReadsetId(), null, referenceGenome.getGenomeId(), description);

        boolean runPangolinAndNextClade = getProvider().getParameterByName("runPangolin").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);

        output.addSequenceOutput(loFreqAllVcf, "LoFreq: " + rs.getName(), CATEGORY, rs.getReadsetId(), null, referenceGenome.getGenomeId(), description);

        String[] pangolinData = null;
        if (runPangolinAndNextClade)
        {
            pangolinData = PangolinHandler.runPangolin(consensusFastaLoFreq, getPipelineCtx().getLogger(), output);

            File json = NextCladeHandler.runNextClade(consensusFastaLoFreq, getPipelineCtx().getLogger(), output, outputDir);
            output.addSequenceOutput(json, "Nextclade: " + rs.getName(), "NextClade JSON", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);
        }

        //write metrics:
        try (CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedUtf8Writing(getMetricsFile(outputDir)), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            writer.writeNext(new String[]{"Category", "MetricName", "Value"});
            writer.writeNext(new String[]{"LoFreq Analysis", "CoverageThreshold", String.valueOf(minCoverage)});
            writer.writeNext(new String[]{"LoFreq Analysis", "TotalConsensusN", String.valueOf(lofreqConsensusNs)});
            writer.writeNext(new String[]{"LoFreq Analysis", "LowCoverPositionsSkipped", String.valueOf(positionsSkipped)});
            writer.writeNext(new String[]{"LoFreq Analysis", "VariantGTThreshold", String.valueOf(totalGTThreshold)});
            writer.writeNext(new String[]{"LoFreq Analysis", "VariantGT1", String.valueOf(totalGT1)});
            writer.writeNext(new String[]{"LoFreq Analysis", "VariantGT50", String.valueOf(totalGT50)});
            writer.writeNext(new String[]{"LoFreq Analysis", "IndelsGTThreshold", String.valueOf(totalIndelGTThreshold)});
            writer.writeNext(new String[]{"LoFreq Analysis", "TotalConsensusVariantsInPBS", String.valueOf(totalConsensusInPBS)});
            writer.writeNext(new String[]{"LoFreq Analysis", "MeanCoverage", String.valueOf(avgDepth)});
            writer.writeNext(new String[]{"LoFreq Analysis", "FilteredVariantsRecovered", String.valueOf(filteredVariantsRecovered)});
            writer.writeNext(new String[]{"LoFreq Analysis", "ConsensusFilteredVariantsRecovered", String.valueOf(consensusFilteredVariantsRecovered)});
            writer.writeNext(new String[]{"LoFreq Analysis", "TotalPindelConsensusVariants", String.valueOf(totalPindelConsensusVariants)});

            if (pangolinData != null)
            {
                writer.writeNext(new String[]{"Pangolin", "PangolinLineage", pangolinData[0]});
                writer.writeNext(new String[]{"Pangolin", "PangolinConflicts", pangolinData[1]});
                writer.writeNext(new String[]{"Pangolin", "PangolinAmbiguity", pangolinData[2]});
                writer.writeNext(new String[]{"Pangolin", "PangolinVersions", pangolinData[3]});
            }
            else
            {
                writer.writeNext(new String[]{"Pangolin", "PangolinLineage", "QC Fail"});
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return output;
    }

    private File getAllVcf(File outputDir, File inputBam)
    {
        return new File(outputDir, FileUtil.getBaseName(inputBam) + ".lofreq.all.vcf.gz");
    }

    private Set<String> runBcftools(File inputBam, ReferenceGenome referenceGenome, File mask, int minCoverage) throws PipelineJobException
    {
        Set<String> variantsBcftools = new HashSet<>();
        File script = new File(SequenceAnalysisService.get().getScriptPath(SequenceAnalysisModule.NAME, "external/viral_consensus.sh"));
        if (!script.exists())
        {
            throw new PipelineJobException("Unable to find script: " + script.getPath());
        }

        SimpleScriptWrapper consensusWrapper = new SimpleScriptWrapper(getPipelineCtx().getLogger());
        consensusWrapper.setWorkingDir(inputBam.getParentFile());

        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (maxThreads != null)
        {
            consensusWrapper.addToEnvironment("SEQUENCEANALYSIS_MAX_THREADS", maxThreads.toString());
        }
        consensusWrapper.execute(Arrays.asList("/bin/bash", script.getPath(), inputBam.getPath(), referenceGenome.getWorkingFastaFile().getPath(), mask.getPath(), String.valueOf(minCoverage)));
        File calls = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + ".calls.vcf.gz");

        try (VCFFileReader reader = new VCFFileReader(calls);CloseableIterator<VariantContext> it = reader.iterator())
        {
            while (it.hasNext())
            {
                VariantContext vc = it.next();

                //NOTE: LoFreq always reports each allele on a different line, so track separately:
                for (Genotype g : vc.getGenotypes())
                {
                    for (Allele a : g.getAlleles())
                    {
                        if (!a.isReference())
                        {
                            String key = vc.getContig() + "<>" + vc.getStart() + a.getBaseString();
                            variantsBcftools.add(key);
                        }
                    }
                }
            }
        }

        return variantsBcftools;
    }

    private File getConsensusVcf(File outputDir, File inputBam)
    {
        return new File(outputDir, FileUtil.getBaseName(inputBam) + ".lofreq.consensus.vcf.gz");
    }

    private File getConsensusFasta(File loFreqConsensusVcf)
    {
        return new File(loFreqConsensusVcf.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(loFreqConsensusVcf.getName()) + ".fasta");
    }

    private File generateConsensus(File loFreqConsensusVcf, File fasta, File maskBed) throws PipelineJobException
    {
        File ret = getConsensusFasta(loFreqConsensusVcf);
        List<String> args = new ArrayList<>();

        args.add(SequencePipelineService.get().getExeForPackage("BCFTOOLS", "bcftools").getPath());
        args.add("consensus");
        args.add("-f");
        args.add(fasta.getPath());
        args.add("-m");
        args.add(maskBed.getPath());
        args.add("-o");
        args.add(ret.getPath());
        args.add(loFreqConsensusVcf.getPath());

        new SimpleScriptWrapper(getPipelineCtx().getLogger()).execute(args);

        if (!ret.exists())
        {
            throw new PipelineJobException("Unable to find expected file: " + ret.getPath());
        }

        return ret;
    }

    private int replaceFastHeader(File consensusFasta, Readset rs, String suffix) throws PipelineJobException
    {
        AtomicInteger totalN = new AtomicInteger();
        DNASequence seq;
        try (InputStream is = IOUtil.openFileForReading(consensusFasta))
        {
            FastaReader<DNASequence, NucleotideCompound> fastaReader = new FastaReader<>(is, new GenericFastaHeaderParser<>(), new DNASequenceCreator(AmbiguityDNACompoundSet.getDNACompoundSet()));
            LinkedHashMap<String, DNASequence> fastaData = fastaReader.process();

            seq = fastaData.values().iterator().next();
            seq.forEach(nt -> {
                if (nt.getUpperedBase().equals("N")) {
                    totalN.getAndIncrement();
                }
            });
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        //Replace FASTA header:
        try (PrintWriter writer = PrintWriters.getPrintWriter(consensusFasta))
        {
            StringBuilder header = new StringBuilder();
            if (rs.getSubjectId() != null)
            {
                header.append(rs.getSubjectId()).append("|");
            }
            else
            {
                header.append(rs.getName()).append("|");
            }

            header.append(rs.getLibraryType() == null ? rs.getApplication() : rs.getLibraryType());

            if (suffix != null)
            {
                header.append("|").append(suffix);
            }

            writer.println(">" + header);
            writer.println(seq.getSequenceAsString());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return totalN.get();
    }

    private File getMetricsFile(File outDir)
    {
        return new File(outDir, "metrics.txt");
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        File metrics = getMetricsFile(outDir);
        if (metrics.exists())
        {
            getPipelineCtx().getLogger().info("Loading metrics");
            int total = 0;
            TableInfo ti = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("quality_metrics");

            //NOTE: if this job errored and restarted, we may have duplicate records:
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), model.getReadset());
            filter.addCondition(FieldKey.fromString("dataid"), model.getAlignmentFile(), CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("analysis_id"), model.getRowId(), CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("container"), getPipelineCtx().getJob().getContainer().getId(), CompareType.EQUAL);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null);
            if (ts.exists())
            {
                getPipelineCtx().getLogger().info("Deleting existing QC metrics (probably from prior restarted job)");
                ts.getArrayList(Integer.class).forEach(rowid -> {
                    Table.delete(ti, rowid);
                });
            }

            try (CSVReader reader = new CSVReader(Readers.getReader(metrics), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    if ("Category".equals(line[0]))
                    {
                        continue;
                    }

                    Map<String, Object> r = new HashMap<>();
                    r.put("category", line[0]);
                    r.put("metricname", line[1]);

                    String value = line[2];

                    String fieldName = NumberUtils.isCreatable(value) ? "metricvalue" : "qualvalue";
                    r.put(fieldName, value);

                    r.put("analysis_id", model.getRowId());
                    r.put("dataid", model.getAlignmentFile());
                    r.put("readset", model.getReadset());
                    r.put("container", getPipelineCtx().getJob().getContainer());
                    r.put("createdby", getPipelineCtx().getJob().getUser().getUserId());

                    Table.insert(getPipelineCtx().getJob().getUser(), ti, r);
                    total++;
                }

                getPipelineCtx().getJob().getLogger().info("total metrics: " + total);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            throw new PipelineJobException("Unable to find metrics file: " + metrics.getPath());
        }

        boolean dbImport = getProvider().getParameterByName("dbImport").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        if (dbImport)
        {
            importNtSnps(model, inputBam, outDir);
        }
        else
        {
            getPipelineCtx().getLogger().info("NT SNP DB Import not selected");
        }

        boolean runPangolinAndNextClade = getProvider().getParameterByName("runPangolin").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        if (runPangolinAndNextClade)
        {
            //Find the NextClade json:
            File jsonFile = NextCladeHandler.getJsonFile(outDir, getConsensusFasta(getConsensusVcf(outDir, inputBam)));
            if (!jsonFile.exists())
            {
                throw new PipelineJobException("Unable to find NextClade JSON record: " + jsonFile.getPath());
            }

            File vcf = getAllVcf(outDir, inputBam);
            if (!vcf.exists())
            {
                throw new PipelineJobException("Unable to find LoFreq VCF: " + vcf.getPath());
            }

            NextCladeHandler.processAndImportNextCladeAa(getPipelineCtx().getJob(), jsonFile, model.getRowId(), model.getLibraryId(), model.getAlignmentFile(), model.getReadset(), vcf, dbImport);
        }
        else
        {
            getPipelineCtx().getLogger().info("NextClade was not run");
        }

        return null;
    }

    private void importNtSnps(AnalysisModel model, File inputBam, File outDir) throws PipelineJobException
    {
        getPipelineCtx().getLogger().info("Importing variants into DB");

        File vcf = getAllVcf(outDir, inputBam);
        if (!vcf.exists())
        {
            throw new PipelineJobException("Unable to find file: " + vcf.getPath());
        }

        ReferenceGenome genome = SequenceAnalysisService.get().getReferenceGenome(model.getLibraryId(), getPipelineCtx().getJob().getUser());
        ReferenceLibraryHelperImpl helper = new ReferenceLibraryHelperImpl(genome.getWorkingFastaFile(), getPipelineCtx().getLogger());

        ViralSnpUtil.deleteExistingValues(getPipelineCtx().getJob(), model.getAnalysisId(), SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS, null);
        Double afThreshold = getProvider().getParameterByName("dbImportThreshold").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Double.class, 0.0);
        Integer depthTheshold = getProvider().getParameterByName("dbImportDepthThreshold").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 0);

        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS);

        try (VCFFileReader reader = new VCFFileReader(vcf);CloseableIterator<VariantContext> it = reader.iterator())
        {
            while (it.hasNext())
            {
                VariantContext vc = it.next();

                if (vc.isFiltered())
                {
                    continue;
                }

                double pct = vc.getAttributeAsDouble("AF", 0.0);
                if (pct < afThreshold)
                {
                    continue;
                }

                int depth = vc.getAttributeAsInt("GATK_DP", 0);
                if (depth < depthTheshold)
                {
                    continue;
                }

                if (vc.getAlternateAlleles().size() != 1)
                {
                    throw new PipelineJobException("Expected a single ALT allele");
                }

                Map<String, Object> ntRow = new HashMap<>();
                ntRow.put("analysis_id", model.getAnalysisId());

                Integer refId = helper.resolveSequenceId(vc.getContig());
                if (refId == null)
                {
                    getPipelineCtx().getLogger().error("unknown reference id: [" + vc.getContig() + "]");
                }

                ntRow.put("ref_nt_id", refId);
                ntRow.put("ref_nt_position", vc.getStart()); //use 1-based
                ntRow.put("ref_nt_insert_index", 0);
                ntRow.put("ref_nt", vc.getReference().getBaseString());
                ntRow.put("q_nt", vc.getAlternateAllele(0).getBaseString());

                List<Integer> depths = vc.getAttributeAsIntList("DP4", 0);
                int alleleDepth = depths.get(2) + depths.get(3);
                ntRow.put("readcount", alleleDepth);
                ntRow.put("depth", depth);
                ntRow.put("adj_depth", vc.getAttribute("DP"));
                ntRow.put("pct", vc.getAttribute("AF"));
                ntRow.put("container", getPipelineCtx().getJob().getContainer().getEntityId());
                ntRow.put("createdby", getPipelineCtx().getJob().getUser().getUserId());
                ntRow.put("modifiedby", getPipelineCtx().getJob().getUser().getUserId());
                ntRow.put("created", new Date());
                ntRow.put("modified", new Date());

                Table.insert(getPipelineCtx().getJob().getUser(), ti, ntRow);
            }
        }
    }

    private String getHashKey(VariantContext vc)
    {
        return vc.getContig() + "<>" + vc.getStart() + vc.getAlternateAlleles().stream().map(Allele::getBaseString).collect(Collectors.joining(";"));
    }

    public static class LofreqWrapper extends AbstractCommandWrapper
    {
        public LofreqWrapper(Logger log)
        {
            super(log);
        }

        public File executeFilter(File inputVcf, File outputVcf) throws PipelineJobException
        {
            if (outputVcf.exists())
            {
                outputVcf.delete();
            }

            File idx = new File(outputVcf.getPath() + ".tbi");
            if (idx.exists())
            {
                idx.delete();
            }

            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());

            args.add("filter");
            args.add("--verbose");
            args.add("--print-all");
            args.add("-i");
            args.add(inputVcf.getPath());

            args.add("-o");
            args.add(outputVcf.getPath());

            execute(args);

            if (!outputVcf.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + outputVcf.getPath());
            }

            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(outputVcf, getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return outputVcf;
        }

        public File execute(File input, File outputVcf, File fasta, @Nullable Integer threads) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());

            args.add(threads == null ? "call" : "call-parallel");
            if (threads != null)
            {
                args.add("--pp-threads");
                args.add(threads.toString());
            }

            args.add("--force-overwrite");
            args.add("--verbose");
            args.add("--no-default-filter");

            //the tool does not seem reliable about respecting force-overwrite
            if (outputVcf.exists())
            {
                outputVcf.delete();
                File index = new File(outputVcf.getPath() + ".tbi");
                if (index.exists())
                {
                    index.delete();
                }
            }

            args.add("--call-indels");
            args.add("-f");
            args.add(fasta.getPath());

            args.add("-o");
            args.add(outputVcf.getPath());

            args.add(input.getPath());

            execute(args);

            if (!outputVcf.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + outputVcf.getPath());
            }

            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(outputVcf, getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return outputVcf;
        }

        public File addIndelQuals(File inputBam, File outputBam, File fasta) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());
            args.add("indelqual");

            args.add("--dindel");

            args.add("--ref");
            args.add(fasta.getPath());

            args.add("--out");
            args.add(outputBam.getPath());

            args.add(inputBam.getPath());

            execute(args);

            if (!outputBam.exists())
            {
                throw new PipelineJobException("Unable to find file: " + outputBam.getPath());
            }

            return outputBam;
        }

        private File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("LOFREQPATH", "lofreq");
        }
    }

    public static SortingCollection<VariantContext> getVariantSorter(VCFHeader outputHeader) {
        File tmpDir = IOUtil.getDefaultTmpDir();
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }

        return SortingCollection.newInstance(
                VariantContext.class,
                new VCFRecordCodec(outputHeader, true),
                outputHeader.getVCFRecordComparator(),
                MAX_RECORDS_IN_RAM, tmpDir.toPath());
    }

}
