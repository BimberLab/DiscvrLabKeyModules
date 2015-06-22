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
public class CollectInsertSizeMetricsWrapper extends PicardWrapper
{
    public CollectInsertSizeMetricsWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File executeCommand(File inputFile, File outputFile, File histogramFile) throws PipelineJobException
    {
        getLogger().info("Running CollectInsertSizeMetrics: " + inputFile.getPath());

        List<String> params = new LinkedList<>();
        params.add("java");
        params.addAll(getBaseParams());
        params.add("-jar");
        params.add(getJar().getPath());
        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        params.add("MAX_RECORDS_IN_RAM=2000000");
        params.add("INPUT=" + inputFile.getPath());
        params.add("OUTPUT=" + outputFile.getPath());
        params.add("HISTOGRAM_FILE=" + histogramFile.getPath());
        execute(params);

        if (!outputFile.exists())
        {
            //this can occur if the input does not have paired data
            return null;
        }

        return outputFile;
    }

    protected File getJar()
    {
        return getPicardJar("CollectInsertSizeMetrics.jar");
    }
}
