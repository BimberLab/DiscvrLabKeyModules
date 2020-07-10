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
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
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
import java.util.LinkedHashSet;
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

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            String basename = ctx.getParams().getString("basename");
            basename = basename + (basename.endsWith(".") ? "" : ".");

            final double minAfThreshold = ctx.getParams().optDouble(MIN_AF, 0.0);
            final int minDepth = ctx.getParams().optInt(MIN_COVERAGE, 0);

            ctx.getLogger().info("Building whitelist of sites and alleles");
            Map<String, Set<Allele>> siteToAllele = new HashMap<>();
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
                            Set<Allele> alleles = siteToAllele.getOrDefault(getCacheKey(vc.getContig(), vc.getStart()), new LinkedHashSet<>());
                            alleles.addAll(vc.getAlleles());
                            siteToAllele.put(getCacheKey(vc.getContig(), vc.getStart()), alleles);
                            whitelistSites.add(Pair.of(vc.getContig(), vc.getStart()));
                        }
                    }
                }
            }

            ctx.getLogger().info("total sites: " + whitelistSites.size());
            Collections.sort(whitelistSites);

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(genomeIds.iterator().next());
            SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
            Map<String, Integer> contigToOffset = getContigToOffset(dict);

            ctx.getLogger().info("Building merged table");

            File output = new File(ctx.getOutputDir(), basename + "txt");
            int idx = 0;
            try (CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedUtf8Writing(output)))
            {
                writer.writeNext(new String[]{"OutputFileId", "ReadsetId", "Contig", "Start", "Ref", "AltAlleles", "Depth", "RefAF", "AltAFs"});

                for (Pair<String, Integer> site : whitelistSites)
                {
                    for (SequenceOutputFile so : inputFiles)
                    {
                        VCFFileReader reader = getReader(so.getFile());
                        try (CloseableIterator<VariantContext> it = reader.query(site.getLeft(), site.getRight(), site.getRight()))
                        {
                            List<String> line = new ArrayList<>();
                            line.add(String.valueOf(so.getRowid()));
                            line.add(String.valueOf(so.getReadset()));

                            line.add(site.getLeft());
                            line.add(String.valueOf(site.getRight()));
                            String key = getCacheKey(site.getLeft(), site.getRight());

                            Allele refAllele = siteToAllele.get(key).iterator().next();
                            line.add(refAllele.getBaseString());

                            Set<Allele> alleles = siteToAllele.get(key);
                            line.add(alleles.stream().map(Allele::getBaseString).collect(Collectors.joining(";")));

                            if (!it.hasNext())
                            {
                                //No variant was called, so this is either considered all WT, or no-call
                                int depth = getReadDepth(so.getFile(), contigToOffset, site.getLeft(), site.getRight());
                                if (depth < minDepth)
                                {
                                    line.add(String.valueOf(depth));
                                    line.add("ND");
                                    line.add("ND");
                                }
                                else
                                {
                                    line.add(String.valueOf(depth));
                                    line.add("1");
                                    line.add(",0".repeat(alleles.size() - 1).substring(1));
                                }

                                writer.writeNext(line.toArray(new String[]{}));
                                idx++;
                            }
                            else
                            {
                                List<String> toWrite = new ArrayList<>(line);

                                while (it.hasNext())
                                {
                                    VariantContext vc = it.next();
                                    if (vc.getAttribute("GATK_DP") == null)
                                    {
                                        throw new PipelineJobException("Expected GATK_DP annotation on line " + key + " in file: " + so.getFile().getPath());
                                    }

                                    if (vc.getAttribute("AF") == null)
                                    {
                                        throw new PipelineJobException("Expected AF annotation on line " + key + " in file: " + so.getFile().getPath());
                                    }

                                    int depth = vc.getAttributeAsInt("GATK_DP", 0);
                                    toWrite.add(String.valueOf(depth));

                                    if (depth < minDepth)
                                    {
                                        toWrite.add("ND");
                                        toWrite.add("ND");
                                    }
                                    else
                                    {
                                        List<Double> afs = vc.getAttributeAsDoubleList("AF", 0);
                                        if (afs.size() != vc.getAlternateAlleles().size())
                                        {
                                            throw new PipelineJobException("Expected AF annotation on line " + key + " in file: " + so.getFile().getPath());
                                        }

                                        double refAf = 1 - afs.stream().reduce(0.0, Double::sum);
                                        toWrite.add(String.valueOf(refAf));

                                        List<Double> toAdd = new ArrayList<>();
                                        for (Allele a : alleles)
                                        {
                                            if (a.isReference())
                                            {
                                                continue;
                                            }

                                            int aIdx = vc.getAlternateAlleles().indexOf(a);
                                            if (aIdx == -1)
                                            {
                                                toAdd.add(0.0);
                                            }
                                            else
                                            {
                                                toAdd.add(afs.get(aIdx));
                                            }
                                        }

                                        toWrite.add(toAdd.stream().map(String::valueOf).collect(Collectors.joining(";")));
                                    }

                                    writer.writeNext(toWrite.toArray(new String[]{}));
                                    idx++;
                                }
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

        private int getReadDepth(File vcf, Map<String, Integer> contigToOffset, String contig, int position) throws PipelineJobException
        {
            File gatkDepth = new File(vcf.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(vcf.getName()) + ".coverage");
            if (!gatkDepth.exists())
            {
                throw new PipelineJobException("File not found: " + gatkDepth.getPath());
            }

            try (Stream<String> lines = Files.lines(gatkDepth.toPath()))
            {
                int lineNo = contigToOffset.get(contig) + position;
                String[] line = lines.skip(lineNo).findFirst().get().split("\t");

                if (!line[0].equals(contig + ":" + position))
                {
                    throw new PipelineJobException("Incorrect line at " + lineNo + ", expected " + contig + ":" + position + ", but was: " + line[0]);
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
}
