package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 5/10/2016.
 */
public class EstimateLibraryComplexityWrapper extends PicardWrapper
{
    public EstimateLibraryComplexityWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File executeCommand(File inputFile, @Nullable File outputFile) throws PipelineJobException
    {
        getLogger().info(getToolName() + ": " + inputFile.getPath());
        List<File> tempFiles = new ArrayList<>();

        //ensure BAM sorted
        try
        {
            SAMFileHeader.SortOrder order = SequenceUtil.getBamSortOrder(inputFile);

            if (SAMFileHeader.SortOrder.coordinate != order)
            {
                getLogger().info("coordinate sorting BAM, order was: " + (order == null ? "not provided" : order.name()));
                File sorted = new File(inputFile.getParentFile(), FileUtil.getBaseName(inputFile) + ".sorted.bam");
                new SamSorter(getLogger()).execute(inputFile, sorted, SAMFileHeader.SortOrder.coordinate);

                //this indicates we expect to replace the original in place, in which case we should delete the unsorted BAM
                if (outputFile == null)
                {
                    tempFiles.add(inputFile);
                }

                inputFile = sorted;
            }
            else
            {
                getLogger().info("bam is already in coordinate sort order");
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        List<String> params = getBaseArgs();
        inferMaxRecordsInRam(params);
        params.add("INPUT=" + inputFile.getPath());
        params.add("OUTPUT=" + getMetricsFile(inputFile).getPath());

        execute(params);

        File metricsFile = getMetricsFile(inputFile);
        if (!metricsFile.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + metricsFile.getPath());
        }

        if (!tempFiles.isEmpty())
        {
            for (File f : tempFiles)
            {
                getLogger().debug("\tdeleting temp file: " + f.getPath());
                f.delete();
            }
        }

        return metricsFile;
    }

    public File getMetricsFile(File inputFile)
    {
        return new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + "." + getToolName().toLowerCase() + ".metrics");
    }

    protected String getToolName()
    {
        return "EstimateLibraryComplexity";
    }
}
