package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MergeLoFreqVcfHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private static final String MIN_AF = "minAfThreshold";
    private static final String MIN_COVERAGE = "minCoverage";

    public MergeLoFreqVcfHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Merge LoFreq VCFs", "This will merge VCFs generate by LoFreq, considering the DepthOfCoverage data generated during those jobs.", null, Arrays.asList(
                ToolParameterDescriptor.create("basename", "Output Name", "The basename of the output file.", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create(MIN_AF, "Min AF to Include", "A site will be included if there is a passing variant above this AF in at least one sample.", "ldk-numberfield", new JSONObject(){{
                    put("minValue", 0);
                    put("maxValue", 1);
                    put("decimalPrecision", 2);
                }}, 0.01),
                ToolParameterDescriptor.create(MIN_COVERAGE, "Min Coverage To Include", "A site will be reported as ND, unless coverage is above this threshold", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 0);
                }}, 10)
            )
        );
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return LofreqAnalysis.CATEGORY.equals(o.getCategory()) && o.getFile() != null && SequenceUtil.FILETYPE.vcf.getFileType().isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public static class Processor implements SequenceOutputHandler.SequenceOutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        private static final double NO_DATA_VAL = -1.0;

        private class SiteAndAlleles
        {
            private final String _contig;
            private final int _start;
            private final Allele _ref;

            Map<String, Map<Allele, String>> _encounteredAlleles = new HashMap<>();
            List<String> _alternates = new ArrayList<>();

            public SiteAndAlleles(String contig, int start, Allele ref)
            {
                _contig = contig;
                _start = start;
                _ref = ref;
            }

            public String getRenamedAllele(VariantContext vc, Allele toCheck)
            {
                Map<Allele, String> map = _encounteredAlleles.get(getEncounteredKey(vc.getStart(), vc.getReference()));
                if (map == null)
                {
                    throw new IllegalArgumentException("Ref not found at pos " + _start + ": " + vc.toStringWithoutGenotypes() + ", existing: " + StringUtils.join(_encounteredAlleles.keySet(), ";"));
                }

                if (!map.containsKey(toCheck))
                {
                    throw new IllegalArgumentException("Allele not found at pos " + _start + ": " + vc.toStringWithoutGenotypes() + " for allele: " + toCheck.getBaseString() + ", existing: " + map.keySet().stream().map(x -> x.getBaseString() + "-" + map.get(x)).collect(Collectors.joining(";")));
                }

                return map.get(toCheck);
            }

            private String getEncounteredKey(int start, Allele ref)
            {
                return start + ":" + ref.getBaseString();
            }

            public void addSite(VariantContext vc, Logger log)
            {
                Map<Allele, String> alleles = _encounteredAlleles.getOrDefault(getEncounteredKey(vc.getStart(), vc.getReference()), new HashMap<>());
                vc.getAlternateAlleles().forEach(a -> {
                    String translated = extractAlleleForPosition(vc, a, log);
                    if (alleles.containsKey(a) && alleles.get(a) != null && !alleles.get(a).equals(translated))
                    {
                        throw new IllegalArgumentException("Translated allele does not match at pos " + _start + ": " + vc.toStringWithoutGenotypes() + " for allele: " + a.getBaseString() + ", existing: " + alleles.keySet().stream().map(x -> x.getBaseString() + "-" + alleles.get(x)).collect(Collectors.joining(";")));
                    }

                    alleles.put(a, translated);

                    if (translated != null && !_alternates.contains(translated))
                    {
                        _alternates.add(translated);
                    }
                });
                _encounteredAlleles.put(getEncounteredKey(vc.getStart(), vc.getReference()), alleles);
            }

            protected String extractAlleleForPosition(VariantContext vc, Allele a, Logger log)
            {
                int offset = _start - vc.getStart();
                if (offset < 0)
                {
                    log.error("Site located after vc start: " + _start);
                    log.error(vc.toStringWithoutGenotypes());
                    log.error(a.getBaseString());
                }

                //deletion
                if (a.length() <= offset)
                {
                    return Allele.SPAN_DEL.getBaseString();
                }
                else if (offset == 0 && a.length() == 1)
                {
                    return _ref.getBaseString().equals(a.getBaseString()) ? null : a.getBaseString();
                }
                else
                {
                    String ret = a.getBaseString().substring(offset, offset + 1);
                    return _ref.getBaseString().equals(ret) ? null: ret;
                }
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            String basename = ctx.getParams().getString("basename");
            basename = basename + (basename.endsWith(".") ? "" : ".");

            final double minAfThreshold = ctx.getParams().optDouble(MIN_AF, 0.0);
            final int minDepth = ctx.getParams().optInt(MIN_COVERAGE, 0);

            ctx.getLogger().info("Pass 1: Building whitelist of sites");
            Map<String, SiteAndAlleles> siteToAllele = new HashMap<>();
            List<Pair<String, Integer>> whitelistSites = new ArrayList<>();

            Set<Integer> genomeIds = new HashSet<>();

            for (SequenceOutputFile so : inputFiles)
            {
                if (so.getLibrary_id() == null)
                {
                    throw new PipelineJobException("VCF lacks library id: " + so.getRowid());
                }

                genomeIds.add(so.getLibrary_id());
                if (genomeIds.size() > 1)
                {
                    throw new PipelineJobException("Samples use more than one genome.  Genome IDs: " + StringUtils.join(genomeIds, ","));
                }

                try (VCFFileReader reader = new VCFFileReader(so.getFile()); CloseableIterator<VariantContext> it = reader.iterator())
                {
                    while (it.hasNext())
                    {
                        VariantContext vc = it.next();
                        if (vc.getAttribute("AF") == null)
                        {
                            continue;
                        }

                        double af = vc.getAttributeAsDouble("AF", 0.0);
                        if (af >= minAfThreshold)
                        {
                            for (int i = 0; i < vc.getLengthOnReference();i++)
                            {
                                int effectiveStart = vc.getStart() + i;
                                String key = getCacheKey(vc.getContig(), effectiveStart);
                                Allele ref = vc.getLengthOnReference() == 1 ? vc.getReference() : Allele.create(vc.getReference().getBaseString().substring(i, i + 1), true);
                                SiteAndAlleles site = siteToAllele.containsKey(key) ? siteToAllele.get(key) : new SiteAndAlleles(vc.getContig(), effectiveStart, ref);
                                if (!siteToAllele.containsKey(key))
                                {
                                    whitelistSites.add(Pair.of(vc.getContig(), effectiveStart));
                                }
                                siteToAllele.put(key, site);
                            }
                        }
                    }
                }
            }

            ctx.getLogger().info("total sites: " + whitelistSites.size());
            Collections.sort(whitelistSites);

            ctx.getLogger().info("Pass 2: establish alleles per site");
            for (SequenceOutputFile so : inputFiles)
            {
                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    for (Pair<String, Integer> site : whitelistSites)
                    {
                        String key = getCacheKey(site.getLeft(), site.getRight());

                        //NOTE: LoFreq should output one VCF line per allele
                        //NOTE: deletions spanning this site can also be included, with a start position ahead of this.
                        try (CloseableIterator<VariantContext> it = reader.query(site.getLeft(), site.getRight(), site.getRight()))
                        {
                            while (it.hasNext())
                            {
                                VariantContext vc = it.next();
                                if (vc.getAttribute("AF") == null)
                                {
                                    continue;
                                }

                                if (vc.getStart() > site.getRight())
                                {
                                    ctx.getLogger().error("Site located after start: " + site.getRight());
                                    ctx.getLogger().error(vc.toStringWithoutGenotypes());
                                    ctx.getLogger().error(so.getFile().getPath());
                                }

                                //NOTE: the start position of this SiteAndAlleles might differ from the VC
                                siteToAllele.get(key).addSite(vc, ctx.getLogger());
                            }
                        }
                    }
                }
            }

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(genomeIds.iterator().next());
            SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
            Map<String, Integer> contigToOffset = getContigToOffset(dict);

            ctx.getLogger().info("Building merged table");

            File output = new File(ctx.getOutputDir(), basename + "txt");
            int idx = 0;
            try (CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedUtf8Writing(output), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"ReadsetName", "OutputFileId", "ReadsetId", "Contig", "Start", "End", "Ref", "AltAlleles", "GatkDepth", "LoFreqDepth", "RefAF", "AltAFs", "NonRefCount", "AltCounts"});

                for (Pair<String, Integer> site : whitelistSites)
                {
                    for (SequenceOutputFile so : inputFiles)
                    {
                        VCFFileReader reader = getReader(so.getFile());

                        //NOTE: LoFreq should output one VCF line per allele:
                        try (CloseableIterator<VariantContext> it = reader.query(site.getLeft(), site.getRight(), site.getRight()))
                        {
                            List<String> line = new ArrayList<>();
                            line.add(ctx.getSequenceSupport().getCachedReadset(so.getReadset()).getName());
                            line.add(String.valueOf(so.getRowid()));
                            line.add(String.valueOf(so.getReadset()));

                            String key = getCacheKey(site.getLeft(), site.getRight());
                            SiteAndAlleles siteDef = siteToAllele.get(key);
                            if (siteDef._alternates.isEmpty())
                            {
                                continue;
                            }

                            line.add(site.getLeft());
                            line.add(String.valueOf(site.getRight()));
                            line.add(String.valueOf(site.getRight() + siteDef._ref.length() - 1));

                            line.add(siteDef._ref.getBaseString());
                            line.add(StringUtils.join(siteDef._alternates, ";"));

                            if (!it.hasNext())
                            {
                                //No variant was called, so this is either considered all WT, or no-call
                                int depth = getReadDepth(so.getFile(), contigToOffset, site.getLeft(), site.getRight());
                                if (depth < minDepth)
                                {
                                    line.add(String.valueOf(depth));
                                    line.add("ND");
                                    line.add("ND");
                                    line.add("ND");
                                    line.add("ND");
                                }
                                else
                                {
                                    line.add(String.valueOf(depth));
                                    line.add("ND");
                                    line.add("1");
                                    line.add(";0".repeat(siteDef._alternates.size()).substring(1));

                                    line.add("0");
                                    line.add(";0".repeat(siteDef._alternates.size()).substring(1));
                                }

                                writer.writeNext(line.toArray(new String[]{}));
                                idx++;
                            }
                            else
                            {
                                Integer gatkDepth = null;
                                Integer lofreqDepth = null;
                                Double totalAltAf = 0.0;
                                int totalAltDepth = 0;
                                Map<String, Double> alleleToAf = new HashMap<>();
                                Map<String, Integer> alleleToDp = new HashMap<>();

                                while (it.hasNext())
                                {
                                    VariantContext vc = it.next();

                                    if (vc.getStart() > siteDef._start)
                                    {
                                        throw new PipelineJobException("Unexpected variant start.  site: " + siteDef._start + " / vc: " + vc.getStart() + " / " + so.getFile().getPath());
                                    }

                                    if (vc.getAlternateAlleles().size() > 1)
                                    {
                                        throw new PipelineJobException("Expected LoFreq VCFs to have only one alternate allele per line.  line " + key + " in file: " + so.getFile().getPath());
                                    }

                                    if (vc.getAttribute("GATK_DP") == null)
                                    {
                                        throw new PipelineJobException("Expected GATK_DP annotation on line " + key + " in file: " + so.getFile().getPath());
                                    }

                                    if (vc.getAttribute("DP") == null)
                                    {
                                        throw new PipelineJobException("Expected DP annotation on line " + key + " in file: " + so.getFile().getPath());
                                    }

                                    if (vc.getAttribute("AF") == null)
                                    {
                                        throw new PipelineJobException("Expected AF annotation on line " + key + " in file: " + so.getFile().getPath());
                                    }

                                    gatkDepth = vc.getAttributeAsInt("GATK_DP", 0);
                                    lofreqDepth = vc.getAttributeAsInt("DP", 0);
                                    List<Integer> depths = vc.getAttributeAsIntList("DP4", 0);
                                    int alleleDepth = depths.get(2) + depths.get(3);

                                    if (gatkDepth < minDepth)
                                    {
                                        vc.getAlternateAlleles().forEach(a -> {
                                            String translatedAllele = siteDef.getRenamedAllele(vc, a);
                                            if (translatedAllele != null)
                                            {
                                                double val = alleleToAf.getOrDefault(translatedAllele, NO_DATA_VAL);
                                                alleleToAf.put(translatedAllele, val);

                                                int val1 = alleleToDp.getOrDefault(translatedAllele, (int)NO_DATA_VAL);
                                                alleleToDp.put(translatedAllele, val1);
                                            }
                                        });
                                    }
                                    else
                                    {
                                        Double af = vc.getAttributeAsDouble("AF", 0.0);
                                        String a = siteDef.getRenamedAllele(vc, vc.getAlternateAlleles().get(0));
                                        if (a != null)
                                        {
                                            totalAltAf += af;
                                            totalAltDepth += alleleDepth;

                                            double val = alleleToAf.getOrDefault(a, 0.0);
                                            if (val == NO_DATA_VAL)
                                            {
                                                val = 0;
                                            }

                                            val = val + af;
                                            alleleToAf.put(a, val);

                                            int val1 = alleleToDp.getOrDefault(a, 0);
                                            if (val1 == NO_DATA_VAL)
                                            {
                                                val1 = 0;
                                            }

                                            val1 = val1 + alleleDepth;
                                            alleleToDp.put(a, val1);
                                        }
                                    }
                                }

                                List<String> toWrite = new ArrayList<>(line);
                                toWrite.add(String.valueOf(gatkDepth));
                                toWrite.add(String.valueOf(lofreqDepth));
                                toWrite.add(String.valueOf(1 - totalAltAf));

                                //Add AFs in order:
                                List<Object> toAdd = new ArrayList<>();
                                List<Object> toAddDp = new ArrayList<>();
                                for (String a : siteDef._alternates)
                                {
                                    double af = alleleToAf.getOrDefault(a, 0.0);
                                    toAdd.add(af == NO_DATA_VAL ? "ND" : af);

                                    int dp = alleleToDp.getOrDefault(a, 0);
                                    toAddDp.add(dp == NO_DATA_VAL ? "ND" : dp);
                                }
                                toWrite.add(toAdd.stream().map(String::valueOf).collect(Collectors.joining(";")));

                                toWrite.add(String.valueOf(totalAltDepth));
                                toWrite.add(toAddDp.stream().map(String::valueOf).collect(Collectors.joining(";")));

                                writer.writeNext(toWrite.toArray(new String[]{}));
                                idx++;
                            }
                        }

                        if (idx % 500 == 0)
                        {
                            ctx.getLogger().info("Total sites written: " + idx);
                        }
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            for (VCFFileReader reader : readerMap.values())
            {
                try
                {
                    reader.close();
                }
                catch (Throwable e)
                {
                    ctx.getLogger().error("Unable to close reader: " + e.getMessage());
                }
            }

            ctx.getFileManager().addSequenceOutput(output, "Merged LoFreq Variants: " + inputFiles.size() + " VCFs", "Merged LoFreq Variant Table", null, null, genome.getGenomeId(), null);
        }

        private String getCacheKey(String contig, int start)
        {
            return contig + "<>" + start;
        }

        private Map<String, Integer> getContigToOffset(SAMSequenceDictionary dict)
        {
            Map<String, Integer> ret = new HashMap<>();

            //Account for the header line:
            int offset = 1;
            for (SAMSequenceRecord rec : dict.getSequences())
            {
                ret.put(rec.getSequenceName(), offset);
                offset += rec.getSequenceLength();
            }

            return ret;
        }

        private int getReadDepth(File vcf, Map<String, Integer> contigToOffset, String contig, int position1) throws PipelineJobException
        {
            File gatkDepth = new File(vcf.getParentFile(), vcf.getName().replaceAll(".all.vcf.gz", ".coverage"));
            if (!gatkDepth.exists())
            {
                throw new PipelineJobException("File not found: " + gatkDepth.getPath());
            }

            try (Stream<String> lines = Files.lines(gatkDepth.toPath()))
            {
                int lineNo = contigToOffset.get(contig) + position1;
                String[] line = lines.skip(lineNo - 1).findFirst().get().split("\t");

                if (!line[0].equals(contig + ":" + position1))
                {
                    throw new PipelineJobException("Incorrect line at " + lineNo + ", expected " + contig + ":" + position1 + ", but was: " + line[0]);
                }

                return Integer.parseInt(line[1]);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private Map<File, VCFFileReader> readerMap = new HashMap<>();

        private VCFFileReader getReader(File f)
        {
            if (!readerMap.containsKey(f))
            {
                readerMap.put(f, new VCFFileReader(f));
            }

            return readerMap.get(f);
        }
    }

    //Adapted from GATK's GATKVariantContextUtils
    private static Allele determineReferenceAllele(final Allele ref1, final Allele ref2)
    {
        if ( ref1 == null || ref1.length() < ref2.length() )
        {
            return ref2;
        }
        else if ( ref2 == null || ref2.length() < ref1.length())
        {
            return ref1;
        }
        else if ( ref1.length() == ref2.length() && ! ref1.equals(ref2) )
        {
            throw new IllegalArgumentException(String.format("The provided reference alleles do not appear to represent the same position, %s vs. %s", ref1, ref2));
        }
        else
        {
            return ref1;
        }
    }

    private static Map<Allele, Allele> createAlleleMapping(final Allele refAllele, final Allele inputRef, final List<Allele> inputAlts)
    {
        if (refAllele.length() < inputRef.length())
        {
            throw new IllegalArgumentException("BUG: inputRef=" + inputRef + " is longer than refAllele=" + refAllele);
        }

        final byte[] extraBases = Arrays.copyOfRange(refAllele.getBases(), inputRef.length(), refAllele.length());

        final Map<Allele, Allele> map = new LinkedHashMap<>();
        for ( final Allele a : inputAlts )
        {
            if ( isNonSymbolicExtendableAllele(a) )
            {
                Allele extended = Allele.extend(a, extraBases);
                map.put(a, extended);
            } else if (a.equals(Allele.SPAN_DEL))
            {
                map.put(a, a);
            }
        }

        return map;
    }

    private static boolean isNonSymbolicExtendableAllele(final Allele allele)
    {
        return ! (allele.isReference() || allele.isSymbolic() || allele.equals(Allele.SPAN_DEL));
    }
}
