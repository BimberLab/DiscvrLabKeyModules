package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.ValidationStringency;
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
 * Date: 10/29/12
 * Time: 7:55 PM
 */
public class AddOrReplaceReadGroupsWrapper extends PicardWrapper
{
    public AddOrReplaceReadGroupsWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    /**
     * If the output file is null, the original will be deleted and replaced by the fixed BAM
     */
    public File executeCommand(File inputFile, @Nullable File outputFile, String library, String platform, String platformUnit, String sampleName) throws PipelineJobException
    {
        Date start = new Date();
        getLogger().info("Running AddOrReplaceReadGroups: " + inputFile.getPath());

        File outputBam = outputFile == null ? new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + ".readgroups.bam") : outputFile;
        List<String> params = getBaseArgs();
        inferMaxRecordsInRam(params);
        params.add("INPUT=" + inputFile.getPath());

        params.add("OUTPUT=" + outputBam.getPath());

        params.add("RGLB=" + library);
        params.add("RGPL=" + (platform == null ? "ILLUMINA" : platform));
        params.add("RGPU=" + platformUnit);
        params.add("RGSM=" + sampleName);

        execute(params);

        if (!outputBam.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + outputBam.getPath());
        }

        if (outputFile == null)
        {
            try
            {
                inputFile.delete();
                FileUtils.moveFile(outputBam, inputFile);

                //note: if there is a pre-existing index, we need to delete this since it is out of date
                File idx = new File(inputFile.getPath() + ".bai");
                if (idx.exists())
                {
                    getLogger().debug("deleting/recreating BAM index");
                    idx.delete();
                    BuildBamIndexWrapper buildBamIndexWrapper = new BuildBamIndexWrapper(getLogger());
                    buildBamIndexWrapper.setStringency(ValidationStringency.SILENT);
                    buildBamIndexWrapper.executeCommand(inputFile);
                }

                getLogger().info("\tAddOrReplaceReadGroups duration: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true));

                return inputFile;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            getLogger().info("\tAddOrReplaceReadGroups duration: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true));

            return outputFile;
        }
    }

    protected String getToolName()
    {
        return "AddOrReplaceReadGroups";
    }
}
