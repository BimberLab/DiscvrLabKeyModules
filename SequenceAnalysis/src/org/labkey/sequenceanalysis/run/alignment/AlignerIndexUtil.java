package org.labkey.sequenceanalysis.run.alignment;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.api.pipeline.AlignmentStep;
import org.labkey.sequenceanalysis.api.pipeline.DefaultPipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.run.CommandWrapper;

import java.io.File;
import java.io.IOException;

/**
 * Created by bimber on 9/6/2014.
 */
public class AlignerIndexUtil
{
    public static boolean hasCachedIndex(PipelineContext ctx, String name) throws PipelineJobException
    {
        ctx.getLogger().debug("checking whether cached index exists: " + name);

        return verifyOrCreateCachedIndex(ctx, null, null, name);
    }

    public static boolean copyIndexIfExists(PipelineContext ctx, DefaultPipelineStepOutput idxOutput, File outputDir, String name) throws PipelineJobException
    {
        ctx.getLogger().debug("copying index to working dir if exists: " + name);

        return verifyOrCreateCachedIndex(ctx, idxOutput, outputDir, name);
    }

    /**
     * If outputDir is null, files will not be copied.  Otherwise files be be copied to this destination.
     */
    private static boolean verifyOrCreateCachedIndex(PipelineContext ctx, @Nullable DefaultPipelineStepOutput idxOutput, @Nullable File outputDir, String name) throws PipelineJobException
    {
        boolean hasCachedIndex = false;
        if (ctx.getSequenceSupport().getReferenceGenome() != null)
        {
            File webserverIndexDir = new File(ctx.getSequenceSupport().getReferenceGenome().getFastaFile().getParentFile(), name);
            if (webserverIndexDir.exists())
            {
                ctx.getLogger().info("previously created index found, no need to recreate");
                ctx.getLogger().debug(webserverIndexDir.getPath());
                hasCachedIndex = true;

                try
                {
                    if (outputDir != null)
                    {
                        ctx.getLogger().info("copying files to work location");
                        File[] files = webserverIndexDir.listFiles();
                        if (files == null)
                        {
                            return false;
                        }

                        for (File f : files)
                        {
                            File dest = new File(outputDir, FileUtil.relativePath(webserverIndexDir.getPath(), f.getPath()));
                            if (!dest.getParentFile().exists())
                            {
                                dest.getParentFile().mkdirs();
                            }

                            if (!dest.exists())
                            {
                                if (f.isDirectory())
                                {
                                    ctx.getLogger().info("copying directory: " + dest.getPath());
                                    FileUtils.copyDirectory(f, dest);
                                }
                                else
                                {
                                    ctx.getLogger().info("copying file: " + dest.getPath());
                                    FileUtils.copyFile(f, dest);
                                }
                            }
                            else
                            {
                                ctx.getLogger().info("target file exists, will not copy: " + dest.getPath());
                            }

                            if (idxOutput != null)
                            {
                                idxOutput.addDeferredDeleteIntermediateFile(dest);
                            }
                        }

                        ctx.getLogger().info("finished copying files");
                    }
                    else
                    {
                        ctx.getLogger().info("no need to copy files at this time");
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                ctx.getLogger().debug("folder does not exist, nothing to do: " + webserverIndexDir.getPath());
            }
        }
        else
        {
            ctx.getLogger().debug("there is no cached reference fasta, nothing to do");
        }

        return hasCachedIndex;
    }

    public static void saveCachedIndex(boolean hasCachedIndex, PipelineContext ctx, File outputDir, String name, AlignmentStep.IndexOutput output) throws PipelineJobException
    {
        if (!hasCachedIndex && ctx.getSequenceSupport().getReferenceGenome() != null && ctx.getSequenceSupport().getReferenceGenome().getGenomeId() != null)
        {
            File cachingDir = new File(ctx.getSequenceSupport().getReferenceGenome().getFastaFile().getParentFile(), name);
            ctx.getLogger().info("caching index files for future use");
            ctx.getLogger().debug(cachingDir.getPath());

            try
            {
                for (File f : output.getDeferredDeleteIntermediateFiles())
                {
                    File dest = new File(cachingDir, FileUtil.relativePath(outputDir.getPath(), f.getPath()));
                    if (f.isDirectory())
                    {
                        FileUtils.copyDirectory(f, dest);
                    }
                    else
                    {
                        FileUtils.copyFile(f, dest);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
