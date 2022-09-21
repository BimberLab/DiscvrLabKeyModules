package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import org.json.old.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.GeneToNameTranslator;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

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
import java.util.TreeMap;
import java.util.TreeSet;

abstract public class AbstractCombineGeneCountsHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    protected static final String STRAND1 = "Strand1";
    protected static final String STRAND2 = "Strand2";
    protected static final String STRANDED = "Stranded";
    protected static final String UNSTRANDED = "Unstranded";
    protected static final String INFER = "Infer";

    protected static final String ReadsetId = "ReadsetId";
    protected static final String ReadsetName = "ReadsetName";
    protected static final String OutputFileId = "OutputFileId";

    protected static final Set<String> OTHER_IDS = PageFlowUtil.set("N_ambiguous", "N_multimapping", "N_noFeature", "N_unmapped");


    private FileType _fileType;
    private String _toolName;

    public AbstractCombineGeneCountsHandler(String name, String description, boolean allowInferStranded, FileType fileType, String toolName)
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), name, description, new LinkedHashSet<>(Arrays.asList("LDK/field/SimpleCombo.js")), getParams(allowInferStranded));
        _fileType = fileType;
        _toolName = toolName;
    }

    private static List<ToolParameterDescriptor> getParams(boolean allowInferStranded)
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>();
        ret.add(ToolParameterDescriptor.create("name", "Output Name", "This is the name that will be used to describe the output.", "textfield", new JSONObject()
        {{
            put("allowBlank", false);
        }}, null));
        ret.add(ToolParameterDescriptor.createExpDataParam("gtf", "GTF/GFF File", "The GTF/GFF file containing genes for this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
        {{
            put("extensions", Arrays.asList("gtf", "gff"));
            put("width", 400);
            put("allowBlank", false);
        }}, null));
        ret.add(ToolParameterDescriptor.create("skipGenesWithoutData", "Skip Genes Without Data", "If checked, the output table will omit any genes with zero read counts across all samples.", "checkbox", null, false));
        ret.add(ToolParameterDescriptor.create("idInHeader", "Header Value", "Choose which value to use as the header/sample identifier", "ldk-simplecombo", new JSONObject(){{
            put("storeValues", ReadsetId + ";" + ReadsetName + ";" + OutputFileId);
            put("value", ReadsetId);
        }}, ReadsetId));

        if (allowInferStranded)
        {
            ret.add(ToolParameterDescriptor.create(STRANDED, "Strand 1", "Choose whether to treat these data as stranded, unstranded, or to have the script infer the strandedness", "ldk-simplecombo", new JSONObject()
            {{
                put("storeValues", STRAND1 + ";" + STRAND2 + ";" + UNSTRANDED + ";" + INFER);
                put("value", INFER);
            }}, INFER));
        }

        return ret;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getLibrary_id() != null && _fileType.isType(o.getFile());
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
        return new CombineStarGeneCountsHandler.Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            if (!ctx.getParams().containsKey("name"))
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

                    ctx.getSequenceSupport().cacheGenome(SequenceAnalysisService.get().getReferenceGenome(o.getLibrary_id(), ctx.getJob().getUser()));
                }
                else
                {
                    throw new PipelineJobException("No library Id provided for file: " + o.getRowid());
                }

                String idInHeader = ctx.getParams().optString("idInHeader", OutputFileId);
                String val = getHeaderValue(idInHeader, ctx.getSequenceSupport(), o);

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
            }, "Gene Count Table: " + _toolName);

            File outFile = new File(ctx.getOutputDir(), ctx.getParams().getString("name") + ".sampleInfo.txt");
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"RowId", "Name", "ReadsetId", "ReadsetName", "AnalysisId", "SubjectId"});

                Map<Integer, SequenceOutputFile> outputFileMap = new TreeMap<>();
                for (SequenceOutputFile so : inputFiles)
                {
                    outputFileMap.put(so.getRowid(), so);
                }

                for (Integer rowId : outputFileMap.keySet())
                {
                    SequenceOutputFile so = outputFileMap.get(rowId);
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

    public void prepareFiles(JobContext ctx, List<SequenceOutputFile> inputFiles, String actionName, HeaderProvider hp, String outputCategory) throws PipelineJobException
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

        Set<Integer> genomeIds = new HashSet<>();
        inputFiles.forEach(x -> genomeIds.add(x.getLibrary_id()));
        if (genomeIds.size() > 1)
        {
            throw new PipelineJobException("All inputs must be from the same genome!");
        }

        action.addInput(gtfFile, "GTF/GFF file");
        job.getLogger().info("using GTF/GFF file: " + gtfFile.getPath());

        //first build a map of all geneIDs and other attributes
        job.getLogger().info("reading GTF/GFF file");
        GeneToNameTranslator translator = new GeneToNameTranslator(gtfFile, job.getLogger());

        CountResults results = new CountResults(inputFiles.size());

        processOutputFiles(results, inputFiles, params, translator, job, action);

        job.getLogger().info("writing output.  total genes: " + results.distinctGenes.size());

        double sumNonZero = 0.0;
        for (SequenceOutputFile so : inputFiles)
        {
            sumNonZero += results.nonZeroCounts.get(so.getRowid());
        }
        double avgNonZero = sumNonZero / (double)inputFiles.size();

        job.getLogger().info("total non-zero genes per sample (and ratio relative to avg)");
        job.getLogger().info("average: " + avgNonZero);

        for (SequenceOutputFile so : inputFiles)
        {
            long totalNonZero = results.nonZeroCounts.get(so.getRowid());
            double ratio = (double)totalNonZero / avgNonZero;

            job.getLogger().info(so.getRowid() + "/" + so.getName() + ": " + totalNonZero + " (" + ratio + " of avg)");
            if (ratio > 2 || ratio < 0.5)
            {
                job.getLogger().warn("total non zero was more than 2-fold different than the average");
            }

            //TODO: consider a warn threshold based on total features?
        }

        Map<Integer, SequenceOutputFile> outputFileMap = new TreeMap<>();
        for (SequenceOutputFile so : inputFiles)
        {
            outputFileMap.put(so.getRowid(), so);
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

            for (Integer rowId : outputFileMap.keySet())
            {
                header.add(hp.getHeader(ctx, outputFileMap.get(rowId)));
            }

            writer.writeNext(header.toArray(new String[header.size()]));

            Set<String> genesWithoutData = new TreeSet<>();
            for (String geneId : results.distinctGenes)
            {
                List<String> row = new ArrayList<>(inputFiles.size() + 3);
                if (translator.getGeneMap().containsKey(geneId))
                {
                    if (translator.getGeneMap().containsKey(geneId))
                    {
                        row.add(geneId);
                        row.add(translator.getGeneMap().get(geneId).get("gene_name"));
                        row.add(translator.getGeneMap().get(geneId).get("gene_description"));
                    }
                    else
                    {
                        row.add(geneId);
                        row.add("");
                        row.add("");
                    }

                    List<String> toAdd = new ArrayList<>();
                    Integer totalWithData = 0;
                    for (Integer rowId : outputFileMap.keySet())
                    {
                        Double count = results.counts.get(rowId).get(geneId);
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
                        errWriter.write(geneId + "\t" + (translator.getGeneMap().get(geneId).get("gene_name") == null ? "" : translator.getGeneMap().get(geneId).get("gene_name")) + "\t" + (translator.getGeneMap().get(geneId).get("gene_description") == null ? "" : translator.getGeneMap().get(geneId).get("gene_description")) + "\n");
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
        so.setLibrary_id(genomeIds.iterator().next());
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

    protected static class CountResults
    {
        Map<Integer, Map<String, Double>> counts;
        Set<String> distinctGenes = new HashSet<>(5000);
        Map<Integer, Long> nonZeroCounts;

        public CountResults(int inputFilesSize)
        {
            nonZeroCounts = new HashMap<>(inputFilesSize);
        }


    }

    abstract protected void processOutputFiles(CountResults results, List<SequenceOutputFile> inputFiles, JSONObject params, GeneToNameTranslator translator, PipelineJob job, RecordedAction action) throws PipelineJobException;
}
