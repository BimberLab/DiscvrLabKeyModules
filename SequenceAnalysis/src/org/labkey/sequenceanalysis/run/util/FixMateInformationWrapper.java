package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * User: bimber
 * Date: 10/29/12
 * Time: 7:55 PM
 */
public class FixMateInformationWrapper extends PicardWrapper
{
    public FixMateInformationWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    /**
     * If the output file is null, the original will be deleted and replaced by the fixed BAM
     */
    public File executeCommand(File inputFile, @Nullable File outputFile) throws PipelineJobException
    {
        getLogger().info("Fixing Mate Information: " + inputFile.getPath());

        List<String> params = new LinkedList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getPicardJar().getPath());
        params.add(getToolName());
        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        params.add("COMPRESSION_LEVEL=" + getCompressionLevel());
        inferMaxRecordsInRam(params);
        params.add("INPUT=" + inputFile.getPath());
        if (outputFile != null)
            params.add("OUTPUT=" + outputFile.getPath());

        execute(params);

        File expectedOut = outputFile == null ? inputFile : outputFile;
        if (!expectedOut.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + expectedOut.getPath());
        }

        return expectedOut;
    }

    protected String getToolName()
    {
        return "FixMateInformation";
    }
}