package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 8/8/2014.
 */
public class GenotypeGVCFsWrapper extends AbstractGatkWrapper
{
    public GenotypeGVCFsWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, File outputFile, @Nullable List<String> options, boolean doCopyLocal, File... inputGVCFs) throws PipelineJobException
    {
        getLogger().info("Running GATK GenotypeGVCFs");

        ensureDictionary(referenceFasta);

        //ensure indexes
        this.ensureVCFIndexes(inputGVCFs);

        Set<File> toDelete = new HashSet<>();
        List<File> vcfsToProcess = new ArrayList<>();
        if (doCopyLocal)
        {
            getLogger().info("making local copies of gVCFs prior to genotyping");
            vcfsToProcess.addAll(copyVcfsLocally(Arrays.asList(inputGVCFs), toDelete, outputFile.getParentFile(), getLogger(), false));
        }
        else
        {
            vcfsToProcess.addAll(Arrays.asList(inputGVCFs));
        }

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("-T");
        args.add("GenotypeGVCFs");
        args.add("-R");
        args.add(referenceFasta.getPath());
        for (File gvcf : vcfsToProcess)
        {
            args.add("--variant");
            args.add(gvcf.getPath());
        }

        args.add("-o");
        args.add(outputFile.getPath());
        args.add("-nda");

        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
        if (maxThreads != null)
        {
            args.add("-nt");
            args.add(maxThreads.toString());
        }

        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
        }

        if (!toDelete.isEmpty())
        {
            getLogger().info("deleting locally copied gVCFs");
            for (File f : toDelete)
            {
                f.delete();
            }
        }
    }

    public static List<File> copyVcfsLocally(Collection<File> inputGVCFs, Collection<File> toDelete, File localWorkDir, Logger log, boolean isResume) throws PipelineJobException
    {
        List<File> vcfsToProcess = new ArrayList<>();
        for (File f : inputGVCFs)
        {
            File origIdx = new File(f.getPath() + ".tbi");
            if (!origIdx.exists())
            {
                throw new PipelineJobException("expected index doesn't exist: " + origIdx.getPath());
            }

            File movedIdx = new File(localWorkDir, f.getName() + ".tbi");
            File movedVcf = new File(localWorkDir, f.getName());
            if (!isResume)
            {
                if (movedIdx.exists())
                {
                    log.debug("moved index exists, skipping file: " + f.getName());
                }
                else
                {
                    log.debug("copying file: " + f.getName());
                    try
                    {
                        if (movedVcf.exists())
                        {
                            movedVcf.delete();
                        }
                        FileUtils.copyFile(f, movedVcf);
                        FileUtils.copyFile(origIdx, movedIdx);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }

            toDelete.add(movedVcf);
            toDelete.add(movedIdx);

            vcfsToProcess.add(movedVcf);
        }

        return vcfsToProcess;
    }

    private void ensureVCFIndexes(File[] inputGVCFs) throws PipelineJobException
    {
        for (File gvcf : inputGVCFs)
        {
            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(gvcf, getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
