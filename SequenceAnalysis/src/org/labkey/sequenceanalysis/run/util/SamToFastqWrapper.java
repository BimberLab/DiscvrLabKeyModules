package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * User: bimber
 * Date: 10/28/12
 * Time: 10:28 PM
 */
public class SamToFastqWrapper extends PicardWrapper
{
    public SamToFastqWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public List<File> extractByReadGroup(File file, File outDir, @Nullable List<String> params) throws PipelineJobException
    {
        getLogger().info("Converting SAM file to FASTQ: " + file.getPath());
        getLogger().info("\tSamToFastq version: " + getVersion());

        List<String> args = getBaseParams(file);
        args.add("INCLUDE_NON_PRIMARY_ALIGNMENTS=FALSE");
        args.add("INCLUDE_NON_PF_READS=TRUE");
        args.add("OUTPUT_PER_RG=TRUE");
        args.add("OUTPUT_DIR=" + outDir.getPath());
        inferMaxRecordsInRam(args);

        execute(args);

        return Arrays.asList(outDir.listFiles());
    }

    public Pair<File, File> executeCommand(File file, String outputName1, @Nullable String outputName2) throws PipelineJobException
    {
        Date start = new Date();
        getLogger().info("Converting SAM file to FASTQ: " + file.getPath());
        getLogger().info("\tSamToFastq version: " + getVersion());

        File workingDir = getOutputDir(file);
        List<String> args = getBaseParams(file);

        File output1 = new File(getOutputDir(file), outputName1);
        args.add("FASTQ=" + output1.getPath());

        if (outputName2 != null)
        {
            File output2 = new File(getOutputDir(file), outputName2);
            args.add("SECOND_END_FASTQ=" + output2.getPath());
        }

        execute(args);
        File output = new File(workingDir, outputName1);
        if (!output.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + output.getPath());
        }

        File output2 = null;
        if (outputName2 != null)
        {
            output2 = new File(workingDir, outputName2);
            if (!output2.exists())
            {
                throw new PipelineJobException("Second output file could not be found: " + output2.getPath());
            }
        }

        getLogger().info("\tSamToFastq duration: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true));

        return Pair.of(output, output2);
    }

    private List<String> getBaseParams(File file) throws PipelineJobException
    {
        List<String> params = getBaseArgs();
        params.add("INPUT=" + file.getPath());

        return params;
    }

    protected String getToolName()
    {
        return "SamToFastq";
    }
}
