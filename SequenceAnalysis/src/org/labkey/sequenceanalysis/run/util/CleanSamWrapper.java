package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * User: bimber
 * Date: 10/29/12
 * Time: 7:55 PM
 */
public class CleanSamWrapper extends PicardWrapper
{
    public CleanSamWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    /**
     * If the output file is null, the original will be deleted and replaced by the fixed BAM
     */
    public File executeCommand(File inputFile, @Nullable File outputFile) throws PipelineJobException
    {
        getLogger().info("Running CleanSam: " + inputFile.getPath());

        List<File> tempFiles = new ArrayList<>();
        File toConvert = inputFile;

        FileType bam = new FileType(".bam");
        if (bam.isType(toConvert))
        {
            getLogger().info("\tconverting BAM to SAM");
            toConvert = new SamFormatConverterWrapper(getLogger()).execute(inputFile, new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + ".converted.sam"), false);
            tempFiles.add(toConvert);
        }

        List<String> params = new LinkedList<>();
        params.add("java");
        params.addAll(getBaseParams());
        params.add("-jar");
        params.add(getJar().getPath());
        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        params.add("INPUT=" + toConvert.getPath());

        File cleanedSam = new SamFormatConverterWrapper(getLogger()).execute(inputFile, new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + ".cleaned.sam"), false);
        tempFiles.add(cleanedSam);
        params.add("OUTPUT=" + cleanedSam.getPath());

        execute(params);

        if (!cleanedSam.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + cleanedSam.getPath());
        }

        File cleanedBam = outputFile;
        if (cleanedBam == null)
        {
            cleanedBam = new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + ".bam");
        }

        File ret = new SamFormatConverterWrapper(getLogger()).execute(cleanedSam, cleanedBam, true);

        for (File tmp : tempFiles)
        {
            if (tmp.exists())
            {
                getLogger().debug("\tDeleting temp file: " + tmp.getPath());
                tmp.delete();
            }
        }

        return ret;
    }

    protected File getJar()
    {
        return getPicardJar("CleanSam.jar");
    }
}
