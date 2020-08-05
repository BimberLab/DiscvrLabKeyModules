package org.labkey.api.sequenceanalysis.pipeline;

import htsjdk.samtools.SAMFileHeader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * User: bimber
 * Date: 6/24/2014
 * Time: 4:08 PM
 */
public class SortSamWrapper extends PicardWrapper
{
    public SortSamWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    //if outputFile is null, the input will be replaced
    public File execute(File inputFile, @Nullable File outputFile, SAMFileHeader.SortOrder order) throws PipelineJobException
    {
        Date start = new Date();
        getLogger().info("Sorting BAM: " + inputFile.getPath());

        File outputBam = outputFile == null ? new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + ".sorted.bam") : outputFile;
        List<String> params = getBaseArgs();
        params.add("INPUT=" + inputFile.getPath());
        params.add("OUTPUT=" + outputBam.getPath());
        params.add("SORT_ORDER=" + order.name());
        inferMaxRecordsInRam(params);

        execute(params);

        if (!outputBam.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + outputBam.getPath());
        }

        if (outputFile == null)
        {
            try
            {
                getLogger().debug("replacing original BAM with sorted: " + order.name());
                inputFile.delete();
                FileUtils.moveFile(outputBam, inputFile);

                //note: if there is a pre-existing index, we need to delete this since it is out of date
                File expectedIndex = new File(inputFile.getPath() + ".bai");
                if (expectedIndex.exists())
                {
                    getLogger().info("deleting out of date index: " + expectedIndex.getPath());
                    expectedIndex.delete();
                }

                if (SAMFileHeader.SortOrder.coordinate == order)
                {
                    SequencePipelineService.get().ensureBamIndex(inputFile, getLogger(), true);
                }

                getLogger().info("\tSortSam duration: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true));

                return inputFile;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            getLogger().info("\tSortSam duration: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true));

            return outputFile;
        }
    }

    @Override
    protected String getToolName()
    {
        return "SortSam";
    }
}
