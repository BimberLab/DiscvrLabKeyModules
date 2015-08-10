package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 12/15/12
 * Time: 9:07 PM
 */
public class SamSorter extends SamtoolsRunner
{
    private static String COMMAND = "sort";

    public SamSorter(@Nullable Logger logger)
    {
        super(logger);
    }

    public File execute(File input, boolean replaceOriginal) throws PipelineJobException
    {
        getLogger().info("Sorting SAM/BAM: " + input.getPath());

        File outputPrefixFile = new File(getOutputDir(input), FileUtil.getBaseName(input) + ".sorted");
        File output = new File(outputPrefixFile.getPath() + "." + FileUtil.getExtension(input));

        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add(COMMAND);
        params.add(input.getPath());
        params.add(outputPrefixFile.getPath());

        execute(params);

        if (!output.exists())
            throw new PipelineJobException("Output BAM not created, expected: " + output.getPath());

        if (replaceOriginal)
        {
            input.delete();
            try
            {
                getLogger().info("\treplacing original file with sorted BAM");
                FileUtils.moveFile(output, input);

                //note: if there is a pre-existing index, we need to delete this since it is out of date
                File idx = new File(input.getPath() + ".bai");
                if (idx.exists())
                {
                    getLogger().debug("deleting/recreating BAM index");
                    idx.delete();
                    BuildBamIndexWrapper buildBamIndexWrapper = new BuildBamIndexWrapper(getLogger());
                    buildBamIndexWrapper.setStringency(ValidationStringency.SILENT);
                    buildBamIndexWrapper.executeCommand(input);
                }

                output = input;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return output;
    }
}
