package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.LinkedList;
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

    public Pair<File, File> executeCommand(File file, String outputName1, @Nullable String outputName2) throws PipelineJobException
    {
        getLogger().info("Converting SAM file to FASTQ: " + file.getPath());
        getLogger().info("\tSamToFastq version: " + getVersion());

        File workingDir = getOutputDir(file);
        execute(getParams(file, outputName1, outputName2));
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

        return Pair.of(output, output2);
    }

    private List<String> getParams(File file, String outputName1, @Nullable String outputName2) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-jar");
        params.add(getJar().getPath());

        params.add("INPUT=" + file.getPath());
        File output1 = new File(getOutputDir(file), outputName1);
        params.add("FASTQ=" + output1.getPath());

        if (outputName2 != null)
        {
            File output2 = new File(getOutputDir(file), outputName2);
            params.add("SECOND_END_FASTQ=" + output2.getPath());
        }

        return params;
    }

    protected File getJar()
    {
        return getPicardJar("SamToFastq.jar");
    }

    public String getOutputFilename(File file)
    {
        return FileUtil.getBaseName(file) + ".fastq";
    }
}
