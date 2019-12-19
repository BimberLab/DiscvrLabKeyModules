package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.api.util.FileType;

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
public class GenotypeGVCFsWrapper extends AbstractGatk4Wrapper
{
    public GenotypeGVCFsWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, File outputFile, @Nullable List<String> options, boolean doCopyLocal, File... inputGVCFs) throws PipelineJobException
    {
        getLogger().info("Running GATK 4 GenotypeGVCFs");

        ensureDictionary(referenceFasta);

        //ensure indexes
        this.ensureVCFIndexes(inputGVCFs);

        Set<File> toDelete = new HashSet<>();
        List<File> filesToProcess = new ArrayList<>();
        if (doCopyLocal)
        {
            getLogger().info("making local copies of gVCF/GenomicsDB files prior to genotyping");
            filesToProcess.addAll(copyVcfsLocally(Arrays.asList(inputGVCFs), toDelete, outputFile.getParentFile(), getLogger(), false));
        }
        else
        {
            filesToProcess.addAll(Arrays.asList(inputGVCFs));
        }

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("GenotypeGVCFs");
        args.add("-R");
        args.add(referenceFasta.getPath());
        for (File f : filesToProcess)
        {
            args.add("--variant");
            if (GVCF.isType(f))
            {
                args.add(f.getPath());
            }
            else if (f.isDirectory())
            {
                args.add("gendb://" + f.getPath());
            }
            else
            {
                throw new IllegalArgumentException("Unknown input: " + f.getPath());
            }
        }

        args.add("-O");
        args.add(outputFile.getPath());

        args.add("--annotate-with-num-discovered-alleles");

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
            getLogger().info("deleting locally copied inputs");
            for (File f : toDelete)
            {
                f.delete();
            }
        }
    }

    private static FileType GVCF = new FileType(".g.vcf", FileType.gzSupportLevel.SUPPORT_GZ);

    public static List<File> copyVcfsLocally(Collection<File> inputGVCFs, Collection<File> toDelete, File localWorkDir, Logger log, boolean isResume) throws PipelineJobException
    {
        List<File> vcfsToProcess = new ArrayList<>();
        for (File f : inputGVCFs)
        {
            File origIdx = null;
            File movedIdx = null;
            if (GVCF.isType(f))
            {
                origIdx = new File(f.getPath() + ".tbi");
                if (!origIdx.exists())
                {
                    throw new PipelineJobException("expected index doesn't exist: " + origIdx.getPath());
                }

                movedIdx = new File(localWorkDir, f.getName() + ".tbi");
            }

            File movedFile = new File(localWorkDir, f.getName());
            if (!isResume)
            {
                if (movedIdx != null && movedIdx.exists())
                {
                    log.debug("moved index exists, skipping file: " + f.getName());
                }
                else
                {
                    log.debug("copying file: " + f.getName());
                    try
                    {
                        if (movedFile.exists())
                        {
                            movedFile.delete();
                        }
                        FileUtils.copyFile(f, movedFile);
                        if (origIdx != null)
                        {
                            FileUtils.copyFile(origIdx, movedIdx);
                        }
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }

            toDelete.add(movedFile);
            toDelete.add(movedIdx);

            vcfsToProcess.add(movedFile);
        }

        return vcfsToProcess;
    }

    private void ensureVCFIndexes(File[] inputGVCFs) throws PipelineJobException
    {
        for (File gvcf : inputGVCFs)
        {
            try
            {
                if (GVCF.isType(gvcf))
                {
                    SequenceAnalysisService.get().ensureVcfIndex(gvcf, getLogger());
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
