package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.ValidationStringency;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
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

    public File executeCommand(File inputFile, File outputFile, File histogramFile) throws PipelineJobException
    {
        getLogger().info("Running CollectInsertSizeMetrics: " + inputFile.getPath());
        File idx = new File(inputFile.getPath() + ".bai");
        if (!idx.exists())
        {
            new BuildBamIndexWrapper(getLogger()).executeCommand(inputFile);
        }

        setStringency(ValidationStringency.SILENT);

        List<String> params = getBaseArgs();
        inferMaxRecordsInRam(params);
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

    protected String getToolName()
    {
        return "CollectInsertSizeMetrics";
    }
}
