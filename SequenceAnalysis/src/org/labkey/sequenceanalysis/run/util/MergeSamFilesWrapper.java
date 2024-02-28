package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * User: bimber
 * Date: 10/29/12
 * Time: 7:39 PM
 */
public class MergeSamFilesWrapper extends PicardWrapper
{
    public MergeSamFilesWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    /**
     * If output path is null, the original file will be replaced
     */
    public File execute(List<File> files, @Nullable File outputPath, boolean deleteOriginalFiles) throws PipelineJobException
    {
        Date start = new Date();
        getLogger().info("Merging BAM files: ");
        for (File f : files)
        {
            getLogger().info("\t" + f.getPath());
        }

        getLogger().info("\tMergeSamFiles version: " + getVersion());

        boolean replaceOriginal = false;
        if (outputPath == null)
        {
            outputPath = new File(files.get(0).getPath() + ".tmp");
            getLogger().info("\tOriginal BAM file will be replaced with merged file");
            replaceOriginal = true;
        }

        File output = outputPath;
        execute(getParams(outputPath, files));

        if (!output.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + output.getPath());
        }

        if (deleteOriginalFiles)
        {
            getLogger().debug("\tDeleting input BAM files");
            for (File f : files)
            {
                File idx = SequenceAnalysisService.get().getExpectedBamOrCramIndex(f);

                f.delete();
                if (f.exists())
                    throw new PipelineJobException("Unable to delete file: " + f.getPath());

                if (idx.exists())
                {
                    idx.delete();
                    if (idx.exists())
                        throw new PipelineJobException("Unable to delete file: " + idx.getPath());
                }
            }
        }

        if (replaceOriginal)
        {
            getLogger().debug("replacing original input bam with merged: " + files.get(0).getPath());
            try
            {
                File target = files.get(0);
                if (target.exists())
                {
                    FileUtils.moveFile(target, new File(target.getPath() + ".original"));
                }
                FileUtils.moveFile(output, target);
                output = target;
            }
            catch (IOException e)
            {
                throw new PipelineJobException("Unable to move file: " + output.getPath() + " to " + files.get(0).getPath());
            }
        }

        //create index.  if there is a pre-existing index, we need to delete this since it is out of date
        getLogger().debug("recreating BAM index");
        File idx = SequenceAnalysisService.get().getExpectedBamOrCramIndex(output);
        if (idx.exists())
        {
            idx.delete();
        }
        BuildBamIndexWrapper buildBamIndexWrapper = new BuildBamIndexWrapper(getLogger());
        buildBamIndexWrapper.setStringency(ValidationStringency.SILENT);
        buildBamIndexWrapper.executeCommand(output);

        getLogger().info("\tMergeSamFiles duration: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true));

        return output;
    }

    private List<String> getParams(File outputPath, List<File> files) throws PipelineJobException
    {
        List<String> params = getBaseArgs();

        for (File f : files)
        {
            params.add("--INPUT");
            params.add(f.getPath());
        }

        params.add("--OUTPUT");
        params.add(outputPath.getPath());

        Integer threads = SequencePipelineService.get().getMaxThreads(getLogger());
        if (threads != null && threads > 1)
        {
            params.add("--USE_THREADING");
            params.add("TRUE");
        }

        return params;
    }

    @Override
    protected String getToolName()
    {
        return "MergeSamFiles";
    }
}
