package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 10/29/12
 * Time: 7:55 PM
 */
public class MarkDuplicatesWrapper extends PicardWrapper
{
    public MarkDuplicatesWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    /**
     * If the output file is null, the original will be deleted and replaced by the fixed BAM
     */
    public File executeCommand(File inputFile, @Nullable File outputFile, List<String> options) throws PipelineJobException
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

        File outputBam = outputFile == null ? new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + "." + getToolName().toLowerCase() + ".bam") : outputFile;
        if ((SequenceAnalysisService.get().getExpectedBamOrCramIndex(outputBam)).exists())
        {
            getLogger().info("BAM index already exists, deleting: " + SequenceAnalysisService.get().getExpectedBamOrCramIndex(outputBam));
            SequenceAnalysisService.get().getExpectedBamOrCramIndex(outputBam).delete();
        }

        List<String> params = getBaseArgs();

        // added for compatibility with GATK.  see:
        // http://gatkforums.broadinstitute.org/discussion/2790/indelrealigner-with-markduplicates
        params.add("--PROGRAM_RECORD_ID");
        params.add("null");

        params.add("--INPUT");
        params.add(inputFile.getPath());

        params.add("--OUTPUT");
        params.add(outputBam.getPath());

        if (options != null)
        {
            params.addAll(options);
        }

        File metricsFile = getMetricsFile(inputFile);
        params.add("--METRICS_FILE");
        params.add(metricsFile.getPath());

        execute(params);

        if (!outputBam.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + outputBam.getPath());
        }

        if (!tempFiles.isEmpty())
        {
            for (File f : tempFiles)
            {
                getLogger().debug("\tdeleting temp file: " + f.getPath());
                f.delete();
            }
        }

        if (outputFile == null)
        {
            try
            {
                inputFile.delete();
                FileUtils.moveFile(outputBam, inputFile);

                //note: if there is a pre-existing index, we need to delete this since it is out of date
                File idx = SequenceAnalysisService.get().getExpectedBamOrCramIndex(inputFile);
                if (idx.exists())
                {
                    getLogger().debug("deleting/recreating BAM index");
                    idx.delete();
                    BuildBamIndexWrapper buildBamIndexWrapper = new BuildBamIndexWrapper(getLogger());
                    buildBamIndexWrapper.setStringency(ValidationStringency.SILENT);
                    buildBamIndexWrapper.executeCommand(inputFile);
                }

                return inputFile;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            return outputFile;
        }
    }

    public File getMetricsFile(File inputFile)
    {
        return new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + "." + getToolName().toLowerCase() + ".metrics");
    }

    @Override
    protected String getToolName()
    {
        return "MarkDuplicates";
    }
}
