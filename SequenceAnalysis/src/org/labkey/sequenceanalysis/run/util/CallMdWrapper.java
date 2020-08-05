package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
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
public class CallMdWrapper extends SamtoolsRunner
{
    private static String COMMAND = "calmd";

    public CallMdWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File execute(File inputBam, @Nullable File outputBam, File referenceFasta) throws PipelineJobException
    {
        getLogger().info("Calling MD tags for SAM/BAM: " + inputBam.getPath());

        File output = outputBam == null ? new File(getOutputDir(inputBam), FileUtil.getBaseName(inputBam) + ".calmd." + FileUtil.getExtension(inputBam)) : outputBam;

        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add(COMMAND);
        params.add("-b");
        params.add(inputBam.getPath());
        params.add(referenceFasta.getPath());

        execute(params, output);

        if (!output.exists())
            throw new PipelineJobException("Output BAM not created, expected: " + output.getPath());

        if (outputBam == null)
        {
            inputBam.delete();
            try
            {
                getLogger().info("\treplacing original file with sorted BAM");
                FileUtils.moveFile(output, inputBam);

                //note: if there is a pre-existing index, we need to delete this since it is out of date
                File idx = new File(inputBam.getPath() + ".bai");
                if (idx.exists())
                {
                    getLogger().debug("deleting/recreating BAM index");
                    idx.delete();
                    BuildBamIndexWrapper buildBamIndexWrapper = new BuildBamIndexWrapper(getLogger());
                    buildBamIndexWrapper.setStringency(ValidationStringency.SILENT);
                    buildBamIndexWrapper.executeCommand(inputBam);
                }

                output = inputBam;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return output;
    }
}
