package org.labkey.sequenceanalysis.analysis;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.run.GeneToNameTranslator;
import org.labkey.api.util.FileType;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 9/8/2014.
 */
public class CombineSubreadGeneCountsHandler extends AbstractCombineGeneCountsHandler
{
    private static final FileType _fileType = new FileType("featureCounts.txt", false);

    public CombineSubreadGeneCountsHandler()
    {
        super("Combine Subread Gene Counts", "This will combine the gene count tables from many samples produced by subread into a single table.", false, _fileType, "Subread");
    }

    @Override
    protected void processOutputFiles(CountResults results, List<SequenceOutputFile> inputFiles, JSONObject params, GeneToNameTranslator translator, PipelineJob job, RecordedAction action) throws PipelineJobException
    {
        results.distinctGenes.addAll(translator.getGeneMap().keySet());

        results.counts = new HashMap<>(inputFiles.size());
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
                    if (line.startsWith("#") || line.startsWith("Geneid"))
                    {
                        continue;
                    }

                    String[] cells = line.split("\\s+");
                    String geneId = cells[0];
                    if (OTHER_IDS.contains(geneId))
                    {
                        continue;
                    }

                    results.distinctGenes.add(geneId);

                    Double count = Double.parseDouble(cells[6]);

                    Map<String, Double> countMap = results.counts.get(so.getRowid());
                    if (countMap == null)
                    {
                        countMap = new HashMap<>(Math.max(results.distinctGenes.size() + 500, 5000));
                    }

                    countMap.put(geneId, count);
                    results.counts.put(so.getRowid(), countMap);

                    if (count > 0)
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
    }
}
