package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by bimber on 4/24/2015.
 */
public class AlignmentSummaryMetricsWrapper extends PicardWrapper
{
    public AlignmentSummaryMetricsWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File executeCommand(File inputFile, @Nullable File outputFile) throws PipelineJobException
    {
        getLogger().info("Running AlignmentSummaryMetrics: " + inputFile.getPath());

        List<String> params = new LinkedList<>();
        params.add("java");
        params.addAll(getBaseParams());
        params.add("-jar");
        params.add(getJar().getPath());
        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        params.add("MAX_RECORDS_IN_RAM=2000000");
        params.add("METRIC_ACCUMULATION_LEVEL=ALL_READS");
        params.add("INPUT=" + inputFile.getPath());
        params.add("OUTPUT=" + outputFile.getPath());

        execute(params);

        if (!outputFile.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + outputFile.getPath());
        }

        return outputFile;
    }

    protected File getJar()
    {
        return getPicardJar("CollectAlignmentSummaryMetrics.jar");
    }
}
