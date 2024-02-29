package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.ValidationStringency;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;

import java.io.File;
import java.util.List;

/**
 * Created by bimber on 4/24/2015.
 */
public class CollectInsertSizeMetricsWrapper extends PicardWrapper
{
    public CollectInsertSizeMetricsWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File executeCommand(File inputFile, File outputFile, File histogramFile, File fasta) throws PipelineJobException
    {
        getLogger().info("Running CollectInsertSizeMetrics: " + inputFile.getPath());
        File idx = SequenceAnalysisService.get().getExpectedBamOrCramIndex(inputFile);
        if (!idx.exists())
        {
            new BuildBamIndexWrapper(getLogger()).executeCommand(inputFile);
        }

        setStringency(ValidationStringency.SILENT);

        List<String> params = getBaseArgs();

        params.add("--INPUT");
        params.add(inputFile.getPath());

        params.add("--OUTPUT");
        params.add(outputFile.getPath());

        params.add("-R");
        params.add(fasta.getPath());

        params.add("-H");
        params.add(histogramFile.getPath());

        execute(params);

        if (!outputFile.exists())
        {
            //this can occur if the input does not have paired data
            return null;
        }

        return outputFile;
    }

    @Override
    protected String getToolName()
    {
        return "CollectInsertSizeMetrics";
    }
}
