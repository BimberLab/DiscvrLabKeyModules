package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
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

        File outputBam = outputFile == null ? new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + ".cleaned.bam") : outputFile;

        List<String> params = getBaseArgs();

        params.add("--INPUT");
        params.add(inputFile.getPath());

        params.add("--OUTPUT");
        params.add(outputBam.getPath());

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
                File idx = SequenceAnalysisService.get().getExpectedBamOrCramIndex(inputFile);
                if (idx.exists())
                {
                    getLogger().debug("deleting/recreating BAM index");
                    idx.delete();
                    BuildBamIndexWrapper buildBamIndexWrapper = new BuildBamIndexWrapper(getLogger());
                    buildBamIndexWrapper.setStringency(ValidationStringency.SILENT);
                    buildBamIndexWrapper.executeCommand(inputFile);
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

    @Override
    protected String getToolName()
    {
        return "CleanSam";
    }
}
