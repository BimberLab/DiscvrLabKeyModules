package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import org.json.JSONObject;
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
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
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
    private FileType _fileType = new FileType("ReadsPerGene.out.tab", false);

    public CombineStarGeneCountsHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Combine STAR Gene Counts", "This will combine the gene count tables from many samples produced by STAR into a single table.", null, Arrays.asList(
                ToolParameterDescriptor.create("name", "Output Name", "This is the name that will be used to describe the output.", "textfield", new JSONObject()
                {{
                        put("allowBlank", false);
                    }}, null),
                ToolParameterDescriptor.createExpDataParam("gtf", "GTF File", "The GTF file containing genes for this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                {{
                        put("extensions", Arrays.asList("gtf"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null)
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
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            JSONObject params = ctx.getParams();

            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            String name = params.getString("name");

            int gtf = params.optInt("gtf");
            if (gtf == 0)
            {
                throw new PipelineJobException("No GTF file provided");
            }

            File gtfFile = ctx.getSequenceSupport().getCachedData(gtf);
            if (gtfFile == null || !gtfFile.exists())
            {
                throw new PipelineJobException("Unable to find GTF file: " + gtfFile);
            }
            action.addInput(gtfFile, "GTF file");
            job.getLogger().info("using GTF file: " + gtfFile.getPath());

            //first build a map of all geneIDs and other attributes
            job.getLogger().info("reading GTF file");
            Map<String, Map<String, String>> geneMap = new HashMap<>();
            try (BufferedReader gtfReader = Readers.getReader(gtfFile))
            {
                Pattern geneIdPattern = Pattern.compile("(gene_id \")([^\"]+)\"(.*)");

                Map<String, Pattern> patternMap = new HashMap<>();
                patternMap.put("transcript_id", Pattern.compile("(transcript_id \")([^\"]+)\"(.*)"));
                patternMap.put("gene_name", Pattern.compile("(gene_name \")([^\"]+)\"(.*)"));
                patternMap.put("gene_description", Pattern.compile("(gene_description \")([^\"]+)\"(.*)"));

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
                                String val = m2.group(2);
                                map.put(field, val);
                            }
                        }

                        String geneId = m1.group(2);
                        geneMap.put(geneId, map);
                    }
                    else
                    {
                        job.getLogger().warn("skipping GTF line because it lacks a gene Id: ");
                        job.getLogger().warn(line);
                    }

                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            //next iterate all read count TSVs to guess strandedness
            double sumStrandRatio = 0.0;
            int countStrandRatio = 0;
            Set<String> distinctGenes = new TreeSet<>();
            Map<Integer, Map<String, Long>> unstrandedCounts = new HashMap<>(inputFiles.size());
            Map<Integer, Map<String, Long>> strandedCounts = new HashMap<>(inputFiles.size());
            for (SequenceOutputFile so : inputFiles)
            {
                job.getLogger().info("reading file: " + so.getFile().getName());
                action.addInput(so.getFile(), "Gene Counts File");
                try (BufferedReader reader = Readers.getReader(so.getFile()))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        line = line.trim();
                        String[] cells = line.split("\\s+");
                        String geneId = cells[0];
                        Long unstranded = Long.parseLong(cells[1]);
                        Long strand1 = Long.parseLong(cells[2]);
                        Long strand2 = Long.parseLong(cells[3]);
                        Long strandMax = Math.max(strand1, strand2);

                        distinctGenes.add(geneId);

                        if (unstranded > 0)
                        {
                            if (strandMax / unstranded > 1)
                            {
                                job.getLogger().info("Gene " + geneId + " will be skipped because it has an out of bounds strandmax/unstranded ratio: " + (strandMax / unstranded));
                                continue;
                            }
                            else
                            {
                                sumStrandRatio += (strandMax / unstranded);
                                countStrandRatio++;
                            }
                        }

                        Map<String, Long> unstrandedMap = unstrandedCounts.get(so.getRowid());
                        if (unstrandedMap == null)
                        {
                            unstrandedMap = new HashMap<>(distinctGenes.size());
                        }

                        Map<String, Long> strandedMap = strandedCounts.get(so.getRowid());
                        if (strandedMap == null)
                        {
                            strandedMap = new HashMap<>(distinctGenes.size());
                        }

                        strandedMap.put(geneId, strandMax);
                        unstrandedMap.put(geneId, unstranded);

                        unstrandedCounts.put(so.getRowid(), unstrandedMap);
                        strandedCounts.put(so.getRowid(), strandedMap);
                    }

                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            //finally build output
            Map<Integer, Map<String, Long>> counts;
            double percent = 100 * sumStrandRatio / countStrandRatio;
            //force unstranded for the time being
            //if (percent > 0.9) {
            if (percent > 999)
            {
                job.getLogger().info("These data appear to be stranded because most of the reads are either on one strand or the other (" + percent + ").  Counts from the strand with the most reads will be used.");
                counts = strandedCounts;
            }
            else
            {
                job.getLogger().info("These data appear to be unstranded because similar numbers of reads are found on strand one and strand two (" + percent + ").  The total unstranded counts will be used.");
                counts = unstrandedCounts;
            }

            job.getLogger().info("writing output.  total genes: " + distinctGenes.size());
            File outputFile = new File(ctx.getOutputDir(), name + ".txt");
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outputFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                //header
                List<String> header = new ArrayList<>();
                header.add("GeneId");
                header.add("TranscriptId");
                header.add("GeneDescription");

                for (SequenceOutputFile so : inputFiles)
                {
                    if (so.getReadset() != null && ctx.getSequenceSupport().getCachedReadset(so.getReadset()) != null)
                    {
                        Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                        header.add(rs.getName());
                    }
                    else
                    {
                        header.add(so.getName());
                    }
                }

                writer.writeNext(header.toArray(new String[header.size()]));

                for (String geneId : distinctGenes)
                {
                    List<String> row = new ArrayList<>();
                    if (geneMap.containsKey(geneId))
                    {
                        row.add(geneMap.get(geneId).containsKey("gene_name") ? geneId + "|" + geneMap.get(geneId).get("gene_name") : geneId);
                        row.add(geneMap.get(geneId).get("transcript_id"));
                        row.add(geneMap.get(geneId).get("gene_description"));

                        for (SequenceOutputFile so : inputFiles)
                        {
                            Long count = counts.get(so.getRowid()).get(geneId);
                            row.add(count == null ? "0" : count.toString());
                        }

                        writer.writeNext(row.toArray(new String[row.size()]));
                    }
                    else
                    {
                        job.getLogger().info("gene not found in GTF: [" + geneId + "]");
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            action.addOutput(outputFile, "Gene Count Table", false);

            SequenceOutputFile so = new SequenceOutputFile();
            so.setCategory("Gene Count Table");
            so.setFile(outputFile);
            so.setName(params.getString("name"));
            ctx.addSequenceOutput(so);

            action.setEndTime(new Date());
            ctx.addActions(action);
        }
    }
}
