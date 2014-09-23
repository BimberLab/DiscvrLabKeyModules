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

    public static boolean copyIndexIfExists(PipelineContext ctx, DefaultPipelineStepOutput idxOutput, File localSharedDir, String name) throws PipelineJobException
    {
        ctx.getLogger().debug("copying index to shared dir if exists: " + name);

        return verifyOrCreateCachedIndex(ctx, idxOutput, localSharedDir, name);
    }

    /**
     * If outputDir is null, files will not be copied.  Otherwise files be be copied to this destination.
     */
    private static boolean verifyOrCreateCachedIndex(PipelineContext ctx, @Nullable DefaultPipelineStepOutput idxOutput, File localSharedDir, String name) throws PipelineJobException
    {
        boolean hasCachedIndex = false;
        if (ctx.getSequenceSupport().getReferenceGenome() != null)
        {
            File webserverIndexDir = new File(ctx.getSequenceSupport().getReferenceGenome().getSourceFastaFile().getParentFile(), name);
            if (webserverIndexDir.exists())
            {
                ctx.getLogger().info("previously created index found, no need to recreate");
                ctx.getLogger().debug(webserverIndexDir.getPath());
                hasCachedIndex = true;

                try
                {
                    if (localSharedDir != null)
                    {
                        ctx.getLogger().info("copying files to work location");
                        File destination = new File(localSharedDir, name);
                        ctx.getLogger().debug(destination.getPath());
                        File[] files = webserverIndexDir.listFiles();
                        if (files == null)
                        {
                            return false;
                        }

                        for (File f : files)
                        {
                            File dest = new File(destination, FileUtil.relativePath(webserverIndexDir.getPath(), f.getPath()));
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

                                if (idxOutput != null)
                                {
                                    idxOutput.addDeferredDeleteIntermediateFile(dest);
                                }
                            }
                            else
                            {
                                ctx.getLogger().info("target file exists, will not copy: " + dest.getPath());
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

    public static void saveCachedIndex(boolean hasCachedIndex, PipelineContext ctx, File indexDir, String name, AlignmentStep.IndexOutput output) throws PipelineJobException
    {
        if (!hasCachedIndex && ctx.getSequenceSupport().getReferenceGenome() != null && ctx.getSequenceSupport().getReferenceGenome().getGenomeId() != null)
        {
            File cachingDir = new File(ctx.getSequenceSupport().getReferenceGenome().getSourceFastaFile().getParentFile(), name);
            ctx.getLogger().info("caching index files for future use");
            ctx.getLogger().debug(cachingDir.getPath());

            try
            {
                File[] files = indexDir.listFiles();
                for (File f : files)
                {
                    File dest = new File(cachingDir, f.getName());
                    ctx.getLogger().debug("copying file: " + dest.getName());
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
