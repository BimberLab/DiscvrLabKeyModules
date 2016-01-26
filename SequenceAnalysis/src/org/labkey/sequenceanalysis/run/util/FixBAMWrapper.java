package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * User: bimber
 * Date: 10/29/12
 * Time: 7:55 PM
 */
public class FixBAMWrapper extends PicardWrapper
{
    public FixBAMWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    /**
     * If the output file is null, the original will be deleted and replaced by the fixed BAM
     */
    public File executeCommand(File inputFile, @Nullable File outputFile) throws PipelineJobException
    {
        getLogger().info("Running FixBAMFile: " + inputFile.getPath());

        List<String> params = new LinkedList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());
        params.add("-classpath");
        params.add(SequenceAnalysisManager.getHtsJdkJar().getPath());
        params.add("htsjdk.samtools.FixBAMFile");

        params.add(inputFile.getPath());

        File cleanedFile = new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + ".cleaned.bam");
        params.add(cleanedFile.getPath());

        execute(params);

        if (!cleanedFile.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + cleanedFile.getPath());
        }

        if (outputFile == null)
        {
            inputFile.delete();
            try
            {
                FileUtils.moveFile(cleanedFile, inputFile);
                cleanedFile = inputFile;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return cleanedFile;
    }

    protected String getTooName()
    {
        throw new UnsupportedOperationException("This tool does not have a standalone JAR");
    }
}
