package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.ValidationStringency;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.List;

/**
 * Created by bimber on 4/24/2015.
 */
public class CollectWgsMetricsWrapper extends PicardWrapper
{
    public CollectWgsMetricsWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File executeCommand(File inputFile, File outputFile, File refFasta) throws PipelineJobException
    {
        getLogger().info("Running " + getToolName() + ": " + inputFile.getPath());
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
        params.add("REFERENCE_SEQUENCE=" + refFasta.getPath());
        params.add("INCLUDE_BQ_HISTOGRAM=true");

        if ("CollectWgsMetricsWithNonZeroCoverage".equals(getToolName()))
        {
            File pdf = new File(outputFile.getParentFile(), FileUtil.getBaseName(outputFile.getName()) + ".wgsMetrics.pdf");
            params.add("CHART_OUTPUT=" + pdf.getPath());
        }

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
        return "CollectWgsMetrics";
    }
}
