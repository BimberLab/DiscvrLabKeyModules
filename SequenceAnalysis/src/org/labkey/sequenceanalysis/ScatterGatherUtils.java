package org.labkey.sequenceanalysis;

import org.apache.commons.io.FileUtils;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;
import org.labkey.sequenceanalysis.run.util.GenotypeGVCFsWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScatterGatherUtils
{
    public static void doCopyGvcfLocally(List<SequenceOutputFile> inputFiles, SequenceOutputHandler.JobContext ctx) throws PipelineJobException
    {
        List<File> inputVCFs = new ArrayList<>();
        inputFiles.forEach(f -> inputVCFs.add(f.getFile()));

        ctx.getLogger().info("making local copies of gVCFs/GenomicsDB");
        GenotypeGVCFsWrapper.copyVcfsLocally(ctx, inputVCFs, new ArrayList<>(), false);
    }

    public static File getLocalCopyDir(SequenceOutputHandler.JobContext ctx, boolean createIfDoesntExist)
    {
        if (ctx.getJob() instanceof VariantProcessingJob)
        {
            return ((VariantProcessingJob)ctx.getJob()).getLocationForCachedInputs(ctx.getWorkDir(), createIfDoesntExist);
        }

        return ctx.getOutputDir();
    }

    public static void possiblyCacheSupportFiles(SequenceOutputHandler.JobContext ctx) throws PipelineJobException
    {
        for (String param : Arrays.asList("exclude_intervals", "forceSitesFile"))
        {
            if (ctx.getParams().get("variantCalling.GenotypeGVCFs." + param) != null)
            {
                File inputFile = ctx.getSequenceSupport().getCachedData(ctx.getParams().getInt("variantCalling.GenotypeGVCFs." + param));
                if (!inputFile.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + inputFile.getPath());
                }

                ctx.getLogger().debug("Making local copy of file: " + inputFile.getName());
                File localCopy = new File(ScatterGatherUtils.getLocalCopyDir(ctx, true), inputFile.getName());
                File doneFile = new File(localCopy.getPath() + ".copyDone");
                if (!doneFile.exists())
                {
                    try
                    {
                        FileUtils.copyFile(inputFile, localCopy);
                        FileUtils.touch(doneFile);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }
        }
    }
}
