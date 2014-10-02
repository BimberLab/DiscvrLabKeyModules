package org.labkey.sequenceanalysis.run.alignment;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.sequenceanalysis.api.pipeline.AlignmentStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;

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

    public static boolean copyIndexIfExists(PipelineContext ctx, AlignmentOutputImpl output, String name) throws PipelineJobException
    {
        ctx.getLogger().debug("copying index to shared dir if exists: " + name);
        if (ctx.getWorkDir() == null)
        {
            throw new PipelineJobException("PipelineContext.getWorkDir() is null");
        }

        return verifyOrCreateCachedIndex(ctx, ctx.getWorkDir(), output, name);
    }

    /**
     * If outputDir is null, files will not be copied.  Otherwise files be be copied to this destination.
     */
    private static boolean verifyOrCreateCachedIndex(PipelineContext ctx, @Nullable WorkDirectory wd, @Nullable AlignmentOutputImpl output, String name) throws PipelineJobException
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
                    if (wd != null)
                    {
                        ctx.getLogger().info("copying index files to work location");
                        File localSharedDir = new File(wd.getDir(), "Shared");
                        File destination = new File(localSharedDir, name);
                        ctx.getLogger().debug(destination.getPath());
                        File[] files = webserverIndexDir.listFiles();
                        if (files == null)
                        {
                            return false;
                        }

                        wd.inputFile(webserverIndexDir, destination, true);
                        output.addDeferredDeleteIntermediateFile(destination);

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
                ctx.getLogger().debug("expected location of cached index does not exist: " + webserverIndexDir.getPath());
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
                    if (f.equals(dest))
                    {
                        ctx.getLogger().debug("source/destination are the same, skipping: " + dest.getName());
                        continue;
                    }

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
