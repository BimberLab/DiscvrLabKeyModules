package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
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
        getLogger().info("Sorting BAM: " + inputFile.getPath());

        File outputBam = outputFile == null ? new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + ".sorted.bam") : outputFile;
        List<String> params = new ArrayList<>();
        params.add("java");
        params.addAll(getBaseParams());
        params.add("-jar");
        params.add(getJar().getPath());

        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        params.add("INPUT=" + inputFile.getPath());
        params.add("OUTPUT=" + outputBam.getPath());
        params.add("SORT_ORDER=" + order.name());

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
                    new BuildBamIndexWrapper(getLogger()).executeCommand(inputFile);
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

    private List<String> getParams(File inputFile, File outputFile) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.addAll(getBaseParams());
        params.add("-jar");
        params.add(getJar().getPath());

        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        params.add("INPUT=" + inputFile.getPath());
        params.add("OUTPUT=" + outputFile.getPath());

        return params;
    }

    @Override
    protected File getJar()
    {
        return getPicardJar("SortSam.jar");
    }
}
