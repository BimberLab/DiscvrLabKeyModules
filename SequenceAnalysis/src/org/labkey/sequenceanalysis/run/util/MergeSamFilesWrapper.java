package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
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

    public File execute(List<File> files, @Nullable String outputPath) throws PipelineJobException
    {
        return execute(files, outputPath, false);
    }

    /**
     * If output path is null, the original file will be replaced
     */
    public File execute(List<File> files, @Nullable String outputPath, boolean deleteOriginalFiles) throws PipelineJobException
    {
        getLogger().info("Merging BAM files: ");
        for (File f : files)
        {
            getLogger().info("\t" + f.getPath());
        }

        getLogger().info("\tMergeSamFiles version: " + getVersion());

        boolean replaceOriginal = false;
        if (outputPath == null)
        {
            outputPath = files.get(0).getPath() + ".tmp";
            getLogger().info("\tOriginal BAM file will be replaced with merged file");
            replaceOriginal = true;
        }

        File output = new File(outputPath);
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
                File idx = new File(f.getPath() + ".bai");

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

        //create index
        BuildBamIndexWrapper idx = new BuildBamIndexWrapper(getLogger());
        idx.setOutputDir(getOutputDir(output));
        idx.executeCommand(output);

        return output;
    }

    private List<String> getParams(String outputPath, List<File> files) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-jar");
        params.add(getJar().getPath());

        for (File f : files)
        {
            params.add("INPUT=" + f.getPath());
        }

        params.add("OUTPUT=" + outputPath);

        return params;
    }

    protected File getJar()
    {
        return getPicardJar("MergeSamFiles.jar");
    }

    public String getOutputFilename(File file)
    {
        throw new RuntimeException("Not supported");
    }
}
