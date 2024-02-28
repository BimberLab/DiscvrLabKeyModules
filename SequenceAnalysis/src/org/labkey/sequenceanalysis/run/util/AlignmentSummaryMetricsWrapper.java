package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.ValidationStringency;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
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

    public File executeCommand(File inputFile, File reference, @Nullable File outputFile) throws PipelineJobException
    {
        getLogger().info("Running AlignmentSummaryMetrics: " + inputFile.getPath());
        setStringency(ValidationStringency.SILENT);
        SequenceAnalysisService.get().ensureBamOrCramIdx(inputFile, getLogger(), false);

        try
        {
            if (SequenceUtil.getBamSortOrder(inputFile) == SAMFileHeader.SortOrder.coordinate)
            {
                List<String> params = getBaseArgs();

                params.add("--METRIC_ACCUMULATION_LEVEL");
                params.add("ALL_READS");

                params.add("--INPUT");
                params.add(inputFile.getPath());

                params.add("-R");
                params.add(reference.getPath());

                params.add("--OUTPUT");
                params.add(outputFile.getPath());

                execute(params);

                if (!outputFile.exists())
                {
                    throw new PipelineJobException("Output file could not be found: " + outputFile.getPath());
                }

                return outputFile;
            }

            getLogger().warn("BAM is not coordinate sorted, skipping AlignmentSummaryMetrics");

            return null;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    protected String getToolName()
    {
        return "CollectAlignmentSummaryMetrics";
    }
}
