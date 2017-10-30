package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bimber on 9/8/2014.
 */
public class CombineStarGeneCountsHandler extends AbstractParameterizedOutputHandler
{
    private FileType _fileType = new FileType(Arrays.asList("ReadsPerGene.out.txt", "ReadsPerGene.out.tab"), "ReadsPerGene.out.txt", false);
    private static final String STRAND1 = "Strand1";
    private static final String STRAND2 = "Strand2";
    private static final String STRANDED = "Stranded";
    private static final String UNSTRANDED = "Unstranded";
    private static final String INFER = "Infer";

    private static final String ReadsetId = "ReadsetId";
    private static final String ReadsetName = "ReadsetName";
    private static final String OutputFileId = "OutputFileId";

    public CombineStarGeneCountsHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Combine STAR Gene Counts", "This will combine the gene count tables from many samples produced by STAR into a single table.", new LinkedHashSet<>(Arrays.asList("LDK/field/SimpleCombo.js")), Arrays.asList(
                ToolParameterDescriptor.create("name", "Output Name", "This is the name that will be used to describe the output.", "textfield", new JSONObject()
                {{
                        put("allowBlank", false);
                    }}, null),
                ToolParameterDescriptor.createExpDataParam("gtf", "GTF/GFF File", "The GTF/GFF file containing genes for this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                {{
                        put("extensions", Arrays.asList("gtf", "gff"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null),
                ToolParameterDescriptor.create("idInHeader", "Header Value", "Choose which value to use as the header/sample identifier", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", ReadsetId + ";" + ReadsetName + ";" + OutputFileId);
                    put("value", ReadsetId);
                }}, ReadsetId),
                ToolParameterDescriptor.create("skipGenesWithoutData", "Skip Genes Without Data", "If checked, the output table will omit any genes with zero read counts across all samples.", "checkbox", null, false),
                ToolParameterDescriptor.create(STRANDED, "Strand 1", "Choose whether to treat these data as stranded, unstranded, or to have the script infer the strandedness", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", STRAND1 + ";" + STRAND2 + ";" + UNSTRANDED + ";"+ INFER);
                    put("value", INFER);
                }}, INFER)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getLibrary_id() != null && _fileType.isType(o.getFile());
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
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
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            if (!params.containsKey("name"))
            {
                throw new PipelineJobException("Must provide the name of the output");
            }

            Integer libraryId = null;
            Set<String> distinctHeaderValues = new CaseInsensitiveHashSet();
            for (SequenceOutputFile o : inputFiles)
            {
                if (o.getLibrary_id() != null)
                {
                    if (libraryId == null)
                    {
                        libraryId = o.getLibrary_id();
                    }

                    if (!libraryId.equals(o.getLibrary_id()))
                    {
                        throw new PipelineJobException("All samples must use the same reference genome");
                    }

                    support.cacheGenome(SequenceAnalysisService.get().getReferenceGenome(o.getLibrary_id(), job.getUser()));
                }
                else
                {
                    throw new PipelineJobException("No library Id provided for file: " + o.getRowid());
                }

                String idInHeader = params.optString("idInHeader", OutputFileId);
                String val = getHeaderValue(idInHeader, support, o);

                if (distinctHeaderValues.contains(val))
                {
                    throw new PipelineJobException("Duplicate values found for gene table headers.  Value was: " + val + " using the field: " + idInHeader);
                }
                distinctHeaderValues.add(val);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            final String idInHeader = ctx.getParams().optString("idInHeader", OutputFileId);

            prepareFiles(ctx, inputFiles, getName(), new HeaderProvider()
            {
                @Override
                public String getHeader(JobContext ctx, SequenceOutputFile so)
                {
                    return getHeaderValue(idInHeader, ctx.getSequenceSupport(), so);
                }
            }, "Gene Count Table");

            File outFile = new File(ctx.getOutputDir(), ctx.getParams().getString("name") + ".sampleInfo.txt");
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"RowId", "Name", "ReadsetId", "ReadsetName", "AnalysisId", "SubjectId"});

                for (SequenceOutputFile so : inputFiles)
                {
                    Readset rs = so.getReadset() != null ? ctx.getSequenceSupport().getCachedReadset(so.getReadset()) : null;

                    writer.writeNext(new String[]{so.getRowid().toString(), so.getName(), appendIfNotNull(so.getReadset()), (rs == null ? "" : rs.getName()), appendIfNotNull(so.getAnalysis_id()), (rs == null ? "" : appendIfNotNull(rs.getSubjectId()))});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    private String appendIfNotNull(Object input)
    {
        return input == null ? "" : String.valueOf(input);
    }

    public static void prepareFiles(JobContext ctx, List<SequenceOutputFile> inputFiles, String actionName, HeaderProvider hp, String outputCategory) throws PipelineJobException
    {
        PipelineJob job = ctx.getJob();
        JSONObject params = ctx.getParams();

        RecordedAction action = new RecordedAction(actionName);
        action.setStartTime(new Date());

        String name = params.getString("name");
        Boolean doSkipGenesWithoutData = params.optBoolean("skipGenesWithoutData", false);
        ctx.getLogger().debug("skip genes without data: " + doSkipGenesWithoutData);

        int gtf = params.optInt("gtf");
        if (gtf == 0)
        {
            throw new PipelineJobException("No GTF file provided");
        }

        File gtfFile = ctx.getSequenceSupport().getCachedData(gtf);
        if (gtfFile == null || !gtfFile.exists())
        {
            throw new PipelineJobException("Unable to find GTF/GFF file: " + gtfFile);
        }
        boolean isGTF = "gtf".equalsIgnoreCase(FileUtil.getExtension(gtfFile));

        action.addInput(gtfFile, "GTF/GFF file");
        job.getLogger().info("using GTF/GFF file: " + gtfFile.getPath());

        //first build a map of all geneIDs and other attributes
        job.getLogger().info("reading GTF/GFF file");
        Map<String, Map<String, String>> geneMap = new HashMap<>(5000);
        int noGeneId = 0;
        try (BufferedReader gtfReader = Readers.getReader(gtfFile))
        {
            Pattern geneIdPattern = isGTF ? Pattern.compile("((gene_id) \")([^\"]+)\"(.*)", Pattern.CASE_INSENSITIVE) : Pattern.compile("((gene_id|geneID)=)([^;]+)(.*)", Pattern.CASE_INSENSITIVE);

            Map<String, Pattern> patternMap = new HashMap<>();
            if (isGTF)
            {
                patternMap.put("transcript_id", Pattern.compile("((transcript_id \"))([^\"]+)\"(.*)", Pattern.CASE_INSENSITIVE));
                patternMap.put("gene_name", Pattern.compile("((gene_name \"))([^\"]+)\"(.*)", Pattern.CASE_INSENSITIVE));
                patternMap.put("gene_description", Pattern.compile("((gene_description \"))([^\"]+)\"(.*)", Pattern.CASE_INSENSITIVE));
            }
            else
            {
                //GFF
                patternMap.put("transcript_id", Pattern.compile("((transcript_id=))([^;]+)(.*)", Pattern.CASE_INSENSITIVE));
                patternMap.put("gene_name", Pattern.compile("((gene_name|gene)[=:])([^;]+)(.*)", Pattern.CASE_INSENSITIVE));
                patternMap.put("gene_description", Pattern.compile("((product|description)=)([^;]+)(.*)", Pattern.CASE_INSENSITIVE));
            }

            String line;
            while ((line = gtfReader.readLine()) != null)
            {
                if (line.startsWith("#"))
                {
                    continue;
                }

                Matcher m1 = geneIdPattern.matcher(line);
                if (m1.find())
                {
                    Map<String, String> map = new HashMap<>();
                    for (String field : patternMap.keySet())
                    {
                        Pattern pattern = patternMap.get(field);
                        Matcher m2 = pattern.matcher(line);
                        if (m2.find())
                        {
                            String val = m2.group(3);
                            map.put(field, val);
                        }
                    }

                    String geneId = m1.group(3);
                    geneMap.put(geneId, map);
                }
                else
                {
                    if (noGeneId < 10)
                    {
                        job.getLogger().warn("skipping GTF/GFF line because it lacks a gene Id: ");
                        job.getLogger().warn(line);

                        if (noGeneId == 9)
                        {
                            job.getLogger().warn("future warnings will be skipped");
                        }
                    }

                    noGeneId++;
                }

            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        if (noGeneId > 0)
        {
            job.getLogger().warn("total lines lacking a gene id: " + noGeneId);
        }

        String strandedSelection = params.optString(STRANDED, INFER);
        final Set<String> OTHER_IDS = PageFlowUtil.set("N_ambiguous", "N_multimapping", "N_noFeature", "N_unmapped");

        //next iterate all read count TSVs to guess strandedness
        double sumStrandRatio = 0.0;
        long countStrandRatio = 0;

        long totalStrand1 = 0L;
        long totalStrand2 = 0L;
        Set<String> distinctGenes = new TreeSet<>();
        distinctGenes.addAll(geneMap.keySet());
        Map<Integer, Map<String, Long>> unstrandedCounts = new HashMap<>(inputFiles.size());
        Map<Integer, Map<String, Long>> strand1Counts = new HashMap<>(inputFiles.size());
        Map<Integer, Map<String, Long>> strand2Counts = new HashMap<>(inputFiles.size());
        Map<Integer, Long> nonZeroCounts = new HashMap<>(inputFiles.size());

        for (SequenceOutputFile so : inputFiles)
        {
            job.getLogger().info("reading file: " + so.getFile().getName());
            action.addInput(so.getFile(), "Gene Counts File");

            if (!nonZeroCounts.containsKey(so.getRowid()))
            {
                nonZeroCounts.put(so.getRowid(), 0L);
            }

            try (BufferedReader reader = Readers.getReader(so.getFile()))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    line = line.trim();
                    String[] cells = line.split("\\s+");
                    String geneId = cells[0];
                    if (OTHER_IDS.contains(geneId))
                    {
                        continue;
                    }

                    Long unstranded = Long.parseLong(cells[1]);
                    Long strand1 = Long.parseLong(cells[2]);
                    totalStrand1 += strand1;
                    Long strand2 = Long.parseLong(cells[3]);
                    totalStrand2 += strand2;
                    Long strandMax = Math.max(strand1, strand2);

                    distinctGenes.add(geneId);

                    if (strandMax > 0)
                    {
                        double ratio = (double)strandMax / (strand1 + strand2);
                        sumStrandRatio += ratio;
                        countStrandRatio++;
                    }

                    Map<String, Long> unstrandedMap = unstrandedCounts.get(so.getRowid());
                    if (unstrandedMap == null)
                    {
                        unstrandedMap = new HashMap<>(Math.max(distinctGenes.size() + 500, 5000));
                    }

                    Map<String, Long> strand1Map = strand1Counts.get(so.getRowid());
                    if (strand1Map == null)
                    {
                        strand1Map = new HashMap<>(Math.max(distinctGenes.size() + 500, 5000));
                    }

                    Map<String, Long> strand2Map = strand2Counts.get(so.getRowid());
                    if (strand2Map == null)
                    {
                        strand2Map = new HashMap<>(Math.max(distinctGenes.size() + 500, 5000));
                    }

                    unstrandedMap.put(geneId, unstranded);
                    strand1Map.put(geneId, strand1);
                    strand2Map.put(geneId, strand2);

                    unstrandedCounts.put(so.getRowid(), unstrandedMap);
                    strand1Counts.put(so.getRowid(), strand1Map);
                    strand2Counts.put(so.getRowid(), strand2Map);

                    if (unstranded > 0 || strand1 > 0 || strand2 > 0)
                    {
                        nonZeroCounts.put(so.getRowid(), nonZeroCounts.get(so.getRowid()) + 1);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        //finally build output
        Map<Integer, Map<String, Long>> counts;
        double avgStrandRatio = sumStrandRatio / countStrandRatio;
        job.getLogger().info("the average stranded/unstranded ratio for all samples was: " + avgStrandRatio);
        double threshold = 0.9;
        String inferredStrandedness;
        job.getLogger().info("Attempting to infer strandedness");
        if (avgStrandRatio > threshold)
        {
            job.getLogger().info("These data appear to be stranded because more than " + (100 * threshold) + "% of the reads are either on one strand or the other (ratio: " + avgStrandRatio + ").  Counts from the strand with the most reads will be used.");
            inferredStrandedness = totalStrand1 > totalStrand2 ? STRAND1 : STRAND2;
            job.getLogger().info("counts for strand 1 vs 2 are: " +  totalStrand1 + "/" + totalStrand2 + ". using " + inferredStrandedness);
        }
        else
        {
            job.getLogger().info("These data appear to be unstranded because similar numbers of reads are found on strand one and strand two (ratio: " + avgStrandRatio + ").  The total unstranded counts will be used.");
            inferredStrandedness = UNSTRANDED;
        }

        if (STRAND1.equalsIgnoreCase(strandedSelection))
        {
            job.getLogger().info("Using strand 1 counts as specified in the job");
            counts = strand1Counts;

            if (!STRAND1.equals(inferredStrandedness))
            {
                job.getLogger().warn("The inferred strandedness doesnt match strand 1.  inferred: " + inferredStrandedness);
            }
        }
        else if (STRAND2.equalsIgnoreCase(strandedSelection))
        {
            job.getLogger().info("Using strand 2 counts as specified in the job");
            counts = strand2Counts;

            if (!STRAND2.equals(inferredStrandedness))
            {
                job.getLogger().warn("The inferred strandedness doesnt match strand 2.  inferred: " + inferredStrandedness);
            }
        }
        else if (UNSTRANDED.equalsIgnoreCase(strandedSelection))
        {
            job.getLogger().info("Using unstranded counts as specified in the job");
            counts = unstrandedCounts;

            if (!UNSTRANDED.equals(inferredStrandedness))
            {
                job.getLogger().warn("The inferred strandedness doesnt match unstranded.  inferred: " + inferredStrandedness);
            }
        }
        else if (INFER.equalsIgnoreCase(strandedSelection))
        {
            job.getLogger().info("Using inferred value for strandedness: " + inferredStrandedness);
            counts = STRAND1.equals(inferredStrandedness) ? strand1Counts: STRAND2.equals(inferredStrandedness) ? strand2Counts : unstrandedCounts;
        }
        else
        {
            throw new PipelineJobException("Unknown value for stranded: " + strandedSelection);
        }

        job.getLogger().info("writing output.  total genes: " + distinctGenes.size());

        double sumNonZero = 0.0;
        for (SequenceOutputFile so : inputFiles)
        {
            sumNonZero += nonZeroCounts.get(so.getRowid());
        }
        double avgNonZero = sumNonZero / (double)inputFiles.size();

        job.getLogger().info("total non-zero genes per sample (and ratio relative to avg)");
        job.getLogger().info("average: " + avgNonZero);

        for (SequenceOutputFile so : inputFiles)
        {
            long totalNonZero = nonZeroCounts.get(so.getRowid());
            double ratio = (double)totalNonZero / avgNonZero;

            job.getLogger().info(so.getRowid() + "/" + so.getName() + ": " + totalNonZero + " (" + ratio + ")");
            if (ratio > 2 || ratio < 0.5)
            {
                job.getLogger().warn("total non zero more than 2-fold different than average");
            }

            //TODO: consider a warn threshold based on total features?
        }

        File outputFile = new File(ctx.getOutputDir(), name + ".txt");
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outputFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            //header
            List<String> header = new ArrayList<>();
            header.add("GeneId");
            header.add("GeneName");
            header.add("GeneDescription");
            header.add("SamplesWithReads");

            for (SequenceOutputFile so : inputFiles)
            {
                header.add(hp.getHeader(ctx, so));
            }

            writer.writeNext(header.toArray(new String[header.size()]));

            Set<String> genesWithoutData = new TreeSet<>();
            for (String geneId : distinctGenes)
            {
                List<String> row = new ArrayList<>(inputFiles.size() + 3);
                if (geneMap.containsKey(geneId))
                {
                    if (geneMap.containsKey(geneId))
                    {
                        row.add(geneId);
                        row.add(geneMap.get(geneId).get("gene_name"));
                        row.add(geneMap.get(geneId).get("gene_description"));
                    }
                    else
                    {
                        row.add(geneId);
                        row.add("");
                        row.add("");
                    }

                    List<String> toAdd = new ArrayList<>();
                    Integer totalWithData = 0;
                    for (SequenceOutputFile so : inputFiles)
                    {
                        Long count = counts.get(so.getRowid()).get(geneId);
                        if (count != null && count > 0)
                        {
                            totalWithData++;
                        }

                        toAdd.add(count == null ? "0" : count.toString());
                    }

                    if (totalWithData > 0 || !doSkipGenesWithoutData)
                    {
                        row.add(totalWithData.toString());
                        row.addAll(toAdd);
                        writer.writeNext(row.toArray(new String[row.size()]));
                    }

                    if (totalWithData == 0 && !OTHER_IDS.contains(geneId))
                    {
                        genesWithoutData.add(geneId);
                    }
                }
                else
                {
                    job.getLogger().error("gene not found in GTF: [" + geneId + "]");
                }
            }

            if (!genesWithoutData.isEmpty())
            {
                File skippedGenes = new File(ctx.getOutputDir(), "genesWithoutData.txt");
                job.getLogger().info("writing list of the " + genesWithoutData.size() + " genes without data to: " + skippedGenes.getPath());
                try (PrintWriter errWriter = PrintWriters.getPrintWriter(skippedGenes))
                {
                    errWriter.write("GeneId\tGeneName\tGeneDescription\n");
                    for (String geneId : genesWithoutData)
                    {
                        errWriter.write(geneId + "\t" + (geneMap.get(geneId).get("gene_name") == null ? "" : geneMap.get(geneId).get("gene_name")) + "\t" + (geneMap.get(geneId).get("gene_description") == null ? "" : geneMap.get(geneId).get("gene_description")) + "\n");
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                ctx.getFileManager().addOutput(action, "Genes Without Data", skippedGenes);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        action.addOutput(outputFile, outputCategory, false);

        SequenceOutputFile so = new SequenceOutputFile();
        so.setCategory(outputCategory);
        so.setFile(outputFile);
        so.setDescription("Total datasets: " + inputFiles.size());
        so.setName(params.getString("name"));
        ctx.addSequenceOutput(so);

        action.setEndTime(new Date());
        ctx.addActions(action);
    }

    public static interface HeaderProvider
    {
        public String getHeader(JobContext ctx, SequenceOutputFile so);
    }

    private static String getHeaderValue(String idInHeader, SequenceAnalysisJobSupport support, SequenceOutputFile so)
    {
        if (idInHeader.equals(ReadsetName))
        {
            Readset rs = support.getCachedReadset(so.getReadset());
            if (rs == null)
            {
                throw new IllegalArgumentException("Readset not found for: " + so.getRowid());
            }

            return rs.getName();

        }
        else if (idInHeader.equals(ReadsetId))
        {
            Readset rs = support.getCachedReadset(so.getReadset());
            if (rs == null)
            {
                throw new IllegalArgumentException("Readset not found for: " + so.getRowid());
            }

            return rs.getReadsetId().toString();
        }
        else
        {
            return so.getRowid().toString();
        }
    }
}
