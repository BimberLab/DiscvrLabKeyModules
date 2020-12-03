package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.analysis.GenotypeGVCFHandler;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
            //args.add("--genomicsdb-use-vcf-codec");
        }
        else if (inputFile.isDirectory())
        {
            args.add("gendb://" + inputFile.getPath());
            //args.add("--genomicsdb-use-vcf-codec");
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

    public static FileType GVCF = new FileType(Arrays.asList(".g.vcf", ".gvcf"), ".g.vcf", FileType.gzSupportLevel.SUPPORT_GZ);

    private static File convertInput(File f)
    {
        return AbstractGenomicsDBImportHandler.TILE_DB_FILETYPE.isType(f) ? f.getParentFile() : f;
    }

    public static List<File> copyVcfsLocally(SequenceOutputHandler.JobContext ctx, Collection<File> inputGVCFs, Collection<File> toDelete, boolean isResume) throws PipelineJobException
    {
        try
        {
            //Note: because we cannot be certain names are unique, inspect:
            HashMap<File, String> inputToDest = new LinkedHashMap<>();
            Set<String> uniqueDestNames = new CaseInsensitiveHashSet();

            inputGVCFs.forEach(x -> {
                x = convertInput(x);
                String fn = x.getName();

                int i = 1;
                while (uniqueDestNames.contains(fn))
                {
                    String basename = SequenceAnalysisService.get().getUnzippedBaseName(x.getName());
                    String ext = x.getName().replaceAll(SequenceAnalysisService.get().getUnzippedBaseName(x.getName()), "");

                    fn = basename + "." + i + ext;
                    i++;
                }

                if (!fn.equals(x.getName()))
                {
                    ctx.getLogger().info("Renaming cached file from: " + x.getName() + " to " + fn);
                }

                uniqueDestNames.add(fn);
                inputToDest.put(x, fn);
            });

            File localWorkDir = GenotypeGVCFHandler.getLocalCopyDir(ctx, true);

            // If the cache directory is under the current working dir, mark to delete when done.
            // If localWorkDir is null, this indicates we're using /tmp, so also delete.
            // Otherwise this indicates a shared cache dir probably used by multiple scatter/gather jobs, and allow the merge task to do cleanup
            boolean reportFilesForDeletion = localWorkDir == null || localWorkDir.getAbsolutePath().startsWith(ctx.getWorkDir().getDir().getAbsolutePath());

            if (localWorkDir == null)
            {
                String tmpDir = StringUtils.trimToNull(PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_TMP_DIR"));
                if (tmpDir == null)
                {
                    tmpDir = System.getProperty("java.io.tmpdir");
                }

                ctx.getLogger().debug("Copying files to temp directory: " + tmpDir);
                localWorkDir = new File(tmpDir);
            }

            List<File> vcfsToProcess = new ArrayList<>();
            for (File f : inputGVCFs)
            {
                f = convertInput(f);
                File destFile = new File(inputToDest.get(f));

                File origIdx = null;
                File movedIdx = null;
                File doneFile = new File(localWorkDir, destFile.getName() + ".copyDone");

                if (GVCF.isType(f))
                {
                    origIdx = new File(f.getPath() + ".tbi");
                    if (!origIdx.exists())
                    {
                        throw new PipelineJobException("expected index doesn't exist: " + origIdx.getPath());
                    }

                    movedIdx = new File(localWorkDir, destFile.getName() + ".tbi");
                }

                File movedFile = new File(localWorkDir, destFile.getName());
                if (!isResume)
                {
                    if (movedIdx != null && movedIdx.exists())
                    {
                        ctx.getLogger().debug("moved index exists, skipping file: " + f.getName());
                    }
                    else if (f.isDirectory() && doneFile.exists())
                    {
                        ctx.getLogger().debug("copied folder exists, skipping file: " + f.getName());
                    }
                    else
                    {
                        long size = f.isDirectory() ? -1 : FileUtils.sizeOf(f);
                        ctx.getLogger().debug("copying file: " + f.getName() + (size != -1 ? ", size: " + FileUtils.byteCountToDisplaySize(size) : ""));
                        if (f.isDirectory())
                        {
                            if (SystemUtils.IS_OS_WINDOWS)
                            {
                                if (movedFile.exists())
                                {
                                    ctx.getLogger().debug("Deleting existing copy of directory: " + movedFile.getPath());
                                    FileUtils.deleteDirectory(movedFile);
                                }

                                ctx.getLogger().debug("Copying directory: " + movedFile.getPath());
                                FileUtils.copyDirectory(f, movedFile);
                            }
                            else
                            {
                                //NOTE: since neither path will end in slashes, rsync to the parent folder should result in the correct placement
                                ctx.getLogger().debug("Copying directory with rsync: " + movedFile.getPath());
                                new SimpleScriptWrapper(ctx.getLogger()).execute(Arrays.asList(
                                    "rsync", "-r", "-a", "--delete", "--no-owner", "--no-group", f.getPath(), movedFile.getParentFile().getPath()
                                ));
                            }

                            FileUtils.touch(doneFile);
                        }
                        else
                        {
                            if (movedFile.exists())
                            {
                                movedFile.delete();
                            }

                            ctx.getLogger().debug("Copying file: " + movedFile.getPath());
                            FileUtils.copyFile(f, movedFile);
                            if (origIdx != null)
                            {
                                FileUtils.copyFile(origIdx, movedIdx);
                            }
                        }
                    }
                }

                if (reportFilesForDeletion)
                {
                    ctx.getLogger().info("Files will be marked for deletion after this step");
                    toDelete.add(movedFile);
                    toDelete.add(movedIdx);
                    if (doneFile.exists())
                    {
                        toDelete.add(doneFile);
                    }
                }

                vcfsToProcess.add(movedFile);
            }

            return vcfsToProcess;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
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
