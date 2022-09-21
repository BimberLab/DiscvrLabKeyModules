package org.labkey.sequenceanalysis.analysis;

import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.run.GeneToNameTranslator;
import org.labkey.api.util.FileType;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 9/8/2014.
 */
public class CombineStarGeneCountsHandler extends AbstractCombineGeneCountsHandler
{
    private static final FileType _fileType = new FileType(Arrays.asList("ReadsPerGene.out.txt", "ReadsPerGene.out.tab"), "ReadsPerGene.out.txt", false);

    public CombineStarGeneCountsHandler()
    {
        super("Combine STAR Gene Counts", "This will combine the gene count tables from many samples produced by STAR into a single table.", true, _fileType, "STAR");
    }

    @Override
    protected void processOutputFiles(CountResults results, List<SequenceOutputFile> inputFiles, JSONObject params, GeneToNameTranslator translator, PipelineJob job, RecordedAction action) throws PipelineJobException
    {

        String strandedSelection = params.optString(STRANDED, INFER);

        //next iterate all read count TSVs to guess strandedness
        double sumStrandRatio = 0.0;
        long countStrandRatio = 0;

        long totalStrand1 = 0L;
        long totalStrand2 = 0L;

        results.distinctGenes.addAll(translator.getGeneMap().keySet());
        Map<Integer, Map<String, Double>> unstrandedCounts = new HashMap<>(inputFiles.size());
        Map<Integer, Map<String, Double>> strand1Counts = new HashMap<>(inputFiles.size());
        Map<Integer, Map<String, Double>> strand2Counts = new HashMap<>(inputFiles.size());

        for (SequenceOutputFile so : inputFiles)
        {
            job.getLogger().info("reading file: " + so.getFile().getName());
            action.addInput(so.getFile(), "Gene Counts File");

            if (!results.nonZeroCounts.containsKey(so.getRowid()))
            {
                results.nonZeroCounts.put(so.getRowid(), 0L);
            }

            try (BufferedReader reader = Readers.getReader(so.getFile()))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    line = line.trim();
                    String[] cells = line.split("\t");
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

                    results.distinctGenes.add(geneId);

                    if (strandMax > 0)
                    {
                        double ratio = (double)strandMax / (strand1 + strand2);
                        sumStrandRatio += ratio;
                        countStrandRatio++;
                    }

                    Map<String, Double> unstrandedMap = unstrandedCounts.get(so.getRowid());
                    if (unstrandedMap == null)
                    {
                        unstrandedMap = new HashMap<>(Math.max(results.distinctGenes.size() + 500, 5000));
                    }

                    Map<String, Double> strand1Map = strand1Counts.get(so.getRowid());
                    if (strand1Map == null)
                    {
                        strand1Map = new HashMap<>(Math.max(results.distinctGenes.size() + 500, 5000));
                    }

                    Map<String, Double> strand2Map = strand2Counts.get(so.getRowid());
                    if (strand2Map == null)
                    {
                        strand2Map = new HashMap<>(Math.max(results.distinctGenes.size() + 500, 5000));
                    }

                    unstrandedMap.put(geneId, unstranded.doubleValue());
                    strand1Map.put(geneId, strand1.doubleValue());
                    strand2Map.put(geneId, strand2.doubleValue());

                    unstrandedCounts.put(so.getRowid(), unstrandedMap);
                    strand1Counts.put(so.getRowid(), strand1Map);
                    strand2Counts.put(so.getRowid(), strand2Map);

                    if (unstranded > 0 || strand1 > 0 || strand2 > 0)
                    {
                        results.nonZeroCounts.put(so.getRowid(), results.nonZeroCounts.get(so.getRowid()) + 1);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        //finally build output
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
            results.counts = strand1Counts;

            if (!STRAND1.equals(inferredStrandedness))
            {
                job.getLogger().warn("The inferred strandedness doesn't match strand 1.  inferred: " + inferredStrandedness);
            }
        }
        else if (STRAND2.equalsIgnoreCase(strandedSelection))
        {
            job.getLogger().info("Using strand 2 counts as specified in the job");
            results.counts = strand2Counts;

            if (!STRAND2.equals(inferredStrandedness))
            {
                job.getLogger().warn("The inferred strandedness doesn't match strand 2.  inferred: " + inferredStrandedness);
            }
        }
        else if (UNSTRANDED.equalsIgnoreCase(strandedSelection))
        {
            job.getLogger().info("Using unstranded counts as specified in the job");
            results.counts = unstrandedCounts;

            if (!UNSTRANDED.equals(inferredStrandedness))
            {
                job.getLogger().warn("The inferred strandedness doesn't match unstranded.  inferred: " + inferredStrandedness);
            }
        }
        else if (INFER.equalsIgnoreCase(strandedSelection))
        {
            job.getLogger().info("Using inferred value for strandedness: " + inferredStrandedness);
            results.counts = STRAND1.equals(inferredStrandedness) ? strand1Counts: STRAND2.equals(inferredStrandedness) ? strand2Counts : unstrandedCounts;
        }
        else
        {
            throw new PipelineJobException("Unknown value for stranded: " + strandedSelection);
        }
    }
}
