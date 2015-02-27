package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
        getLogger().info("Running AddOrReplaceReadGroups: " + inputFile.getPath());

        File outputBam = outputFile == null ? new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + ".readgroups.bam") : outputFile;
        List<String> params = new ArrayList<>();
        params.add("java");
        params.addAll(getBaseParams());
        params.add("-jar");
        params.add(getJar().getPath());
        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
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

    protected File getJar()
    {
        return getPicardJar("AddOrReplaceReadGroups.jar");
    }
}
