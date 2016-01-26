package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
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

    public File executeCommand(File inputFile, File reference, @Nullable File outputFile) throws PipelineJobException
    {
        getLogger().info("Running AlignmentSummaryMetrics: " + inputFile.getPath());

        try
        {
            if (SequenceUtil.getBamSortOrder(inputFile) != SAMFileHeader.SortOrder.coordinate)
            {
                List<String> params = new LinkedList<>();
                params.add(SequencePipelineService.get().getJavaFilepath());
                params.addAll(SequencePipelineService.get().getJavaOpts());
                params.add("-jar");
                params.add(getPicardJar().getPath());
                params.add(getTooName());
                params.add("VALIDATION_STRINGENCY=" + getStringency().name());
                inferMaxRecordsInRam(params);
                params.add("METRIC_ACCUMULATION_LEVEL=ALL_READS");
                params.add("INPUT=" + inputFile.getPath());
                params.add("R=" + reference.getPath());
                params.add("OUTPUT=" + outputFile.getPath());

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

    protected String getTooName()
    {
        return "CollectAlignmentSummaryMetrics";
    }
}
