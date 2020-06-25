package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
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

    public void execute(File referenceFasta, File outputFile, @Nullable List<String> options, File inputFile) throws PipelineJobException
    {
        getLogger().info("Running GATK 4 GenotypeGVCFs");

        ensureDictionary(referenceFasta);

        //ensure indexes
        this.ensureVCFIndexes(inputFile);

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("GenotypeGVCFs");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("--variant");
        if (GVCF.isType(inputFile))
        {
            args.add(inputFile.getPath());
        }
        else if (AbstractGenomicsDBImportHandler.TILE_DB_FILETYPE.isType(inputFile))
        {
            args.add("gendb://" + inputFile.getParentFile().getPath());
            //See: https://github.com/broadinstitute/gatk/issues/6667
            args.add("--genomicsdb-use-vcf-codec");
        }
        else if (inputFile.isDirectory())
        {
            args.add("gendb://" + inputFile.getPath());
            args.add("--genomicsdb-use-vcf-codec");
        }
        else
        {
            throw new IllegalArgumentException("Unknown input: " + inputFile.getPath());
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
    }

    private static FileType GVCF = new FileType(".g.vcf", FileType.gzSupportLevel.SUPPORT_GZ);

    public static List<File> copyVcfsLocally(Collection<File> inputGVCFs, Collection<File> toDelete, File localWorkDir, Logger log, boolean isResume) throws PipelineJobException
    {
        if (localWorkDir == null)
        {
            String tmpDir = StringUtils.trimToNull(PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_TMP_DIR"));
            if (tmpDir == null)
            {
                tmpDir = System.getProperty("java.io.tmpdir");
            }

            log.debug("Copying files to temp directory: " + tmpDir);
            localWorkDir = new File(tmpDir);
        }

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
                    long size = f.isDirectory() ? FileUtils.sizeOfDirectory(f) : FileUtils.sizeOf(f);
                    log.debug("copying file: " + f.getName() + ", size: " + FileUtils.byteCountToDisplaySize(size));
                    try
                    {
                        if (f.isDirectory())
                        {
                            if (movedFile.exists())
                            {
                                FileUtils.deleteDirectory(movedFile);
                            }
                            FileUtils.copyDirectory(f, movedFile);
                        }
                        else
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

    private void ensureVCFIndexes(File inputGVCF) throws PipelineJobException
    {
        try
        {
            if (GVCF.isType(inputGVCF))
            {
                SequenceAnalysisService.get().ensureVcfIndex(inputGVCF, getLogger());
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
