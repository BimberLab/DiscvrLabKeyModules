package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.ValidationStringency;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
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

        params.add("--REFERENCE_SEQUENCE");
        params.add(refFasta.getPath());

        params.add("--INCLUDE_BQ_HISTOGRAM");
        params.add("true");

        if ("CollectWgsMetricsWithNonZeroCoverage".equals(getToolName()))
        {
            File pdf = new File(outputFile.getParentFile(), FileUtil.getBaseName(outputFile.getName()) + ".wgsMetrics.pdf");
            params.add("--CHART_OUTPUT");
            params.add(pdf.getPath());
        }

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
        return "CollectWgsMetrics";
    }
}
