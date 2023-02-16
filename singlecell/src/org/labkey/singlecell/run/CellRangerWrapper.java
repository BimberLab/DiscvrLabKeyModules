package org.labkey.singlecell.run;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CellRangerWrapper extends AbstractCommandWrapper
{
    public static final String GTF_FILE = "GTF File";
    private static final Pattern FILE_PATTERN = Pattern.compile("^(.+?)(_S[0-9]+){0,1}_L(.+?)_(R){0,1}([0-9])(_[0-9]+){0,1}(.*?)(\\.f(ast){0,1}q)(\\.gz)?$");
    private static final Pattern SAMPLE_PATTERN = Pattern.compile("^(.+)_S[0-9]+(.*)$");

    public CellRangerWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    protected File getExe(boolean use31)
    {
        return SequencePipelineService.get().getExeForPackage("CELLRANGERPATH", "cellranger" + (use31 ? "-31" : ""));
    }

    public static Set<File> getRawDataDirs(File outputDir, boolean filteredOnly, boolean includeAnalysis)
    {
        List<String> dirs = new ArrayList<>();
        dirs.add("filtered_feature_bc_matrix");
        dirs.add("filtered_gene_bc_matrices");

        if (!filteredOnly)
        {
            dirs.add("raw_gene_bc_matrices");
            dirs.add("raw_feature_bc_matrix");
        }

        if (includeAnalysis)
        {
            dirs.add("analysis");
        }

        Set<File> toAdd = new HashSet<>();
        for (String dir : dirs)
        {
            File subDir = new File(outputDir, dir);
            if (subDir.exists())
            {
                toAdd.add(subDir);
            }
        }

        return toAdd;
    }

    public File getLocalFastqDir(File outputDirectory)
    {
        return new File(outputDirectory, "localFq");
    }

    public List<String> prepareCountArgs(AlignmentOutputImpl output, String id, File outputDirectory, Readset rs, List<Pair<File, File>> inputFastqPairs, List<String> extraArgs, boolean writeFastqArgs) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe(false).getPath());
        args.add("count");

        args.add("--id=" + id);

        if (extraArgs != null)
        {
            args.addAll(extraArgs);
        }

        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
        if (maxThreads != null)
        {
            args.add("--localcores=" + maxThreads);
        }

        Integer maxRam = SequencePipelineService.get().getMaxRam();
        if (maxRam != null)
        {
            args.add("--localmem=" + maxRam);
        }

        File localFqDir = getLocalFastqDir(outputDirectory);
        output.addIntermediateFile(localFqDir);

        Set<String> sampleNames = prepareFastqSymlinks(rs, localFqDir, inputFastqPairs);
        if (writeFastqArgs)
        {
            args.add("--fastqs=" + localFqDir.getPath());
        }

        getLogger().debug("Sample names: [" + StringUtils.join(sampleNames, ",") + "]");
        if (sampleNames.size() > 1)
        {
            args.add("--sample=" + StringUtils.join(sampleNames, ","));
        }

        return args;
    }

    public Set<String> prepareFastqSymlinks(Readset rs, File localFqDir, List<Pair<File, File>> inputFastqPairs) throws PipelineJobException
    {
        getLogger().info("Possibly preparing symlinks for readset: " + rs.getName());
        Set<String> ret = new HashSet<>();
        if (!localFqDir.exists())
        {
            localFqDir.mkdirs();
        }

        String[] files = localFqDir.list();
        if (files != null && files.length > 0)
        {
            deleteSymlinks(localFqDir);
        }

        int idx = 0;
        boolean doRename = true;  //cellranger is too picky - simply rename files all the time
        for (Pair<File, File> pair : inputFastqPairs)
        {
            idx++;
            try
            {
                File target1 = new File(localFqDir, getSymlinkFileName(pair.getLeft().getName(), doRename, rs.getName(), idx, false));
                createSymLink(pair.getLeft(), target1);
                ret.add(getSampleName(target1.getName()));

                if (pair.getRight() != null)
                {
                    File target2 = new File(localFqDir, getSymlinkFileName(pair.getRight().getName(), doRename, rs.getName(), idx, true));
                    createSymLink(pair.getRight(), target2);
                    ret.add(getSampleName(target2.getName()));
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return ret;
    }

    private void createSymLink(File input, File target) throws IOException
    {
        getLogger().debug("file: " + input.getPath());
        getLogger().debug("target: " + target.getPath());
        if (target.exists())
        {
            getLogger().debug("deleting existing symlink: " + target.getName());
            Files.delete(target.toPath());
        }

        Files.createSymbolicLink(target.toPath(), input.toPath());
    }

    public static String makeLegalSampleName(String sampleName)
    {
        return FileUtil.makeLegalName(sampleName.replaceAll("_", "-")).replaceAll(" ", "-").replaceAll("\\.", "-").replaceAll("\\+", "");
    }

    private String getSymlinkFileName(String fileName, boolean doRename, String sampleName, int idx, boolean isReversed)
    {
        //NOTE: cellranger is very picky about file name formatting
        if (doRename)
        {
            sampleName = makeLegalSampleName(sampleName);
            return sampleName + "_S1_L001_R" + (isReversed ? "2" : "1") + "_" + StringUtils.leftPad(String.valueOf(idx), 3, "0") + ".fastq.gz";
        }
        else
        {
            //NOTE: cellranger is very picky about file name formatting
            Matcher m = FILE_PATTERN.matcher(fileName);
            if (m.matches())
            {
                if (!StringUtils.isEmpty(m.group(7)))
                {
                    return m.group(1).replaceAll("_", "-") + StringUtils.trimToEmpty(m.group(2)) + "_L" + StringUtils.trimToEmpty(m.group(3)) + "_" + StringUtils.trimToEmpty(m.group(4)) + StringUtils.trimToEmpty(m.group(5)) + StringUtils.trimToEmpty(m.group(6)) + ".fastq.gz";
                }
                else if (m.group(1).contains("_"))
                {
                    getLogger().info("replacing underscores in file/sample name");
                    return m.group(1).replaceAll("_", "-") + StringUtils.trimToEmpty(m.group(2)) + "_L" + StringUtils.trimToEmpty(m.group(3)) + "_" + StringUtils.trimToEmpty(m.group(4)) + StringUtils.trimToEmpty(m.group(5)) + StringUtils.trimToEmpty(m.group(6)) + ".fastq.gz";
                }
                else
                {
                    getLogger().info("no additional characters found");
                }
            }
            else
            {
                getLogger().warn("filename does not match Illumina formatting: " + fileName);
            }

            return FileUtil.makeLegalName(fileName);
        }
    }

    private String getSampleName(String fn)
    {
        Matcher matcher = FILE_PATTERN.matcher(fn);
        if (matcher.matches())
        {
            String ret = matcher.group(1);
            Matcher matcher2 = SAMPLE_PATTERN.matcher(ret);
            if (matcher2.matches())
            {
                ret = matcher2.group(1);
            }
            else
            {
                getLogger().debug("_S not found in sample: [" + ret + "]");
            }

            ret = ret.replaceAll("_", "-");

            return ret;
        }

        throw new IllegalArgumentException("Unable to infer Illumina sample name: " + fn);
    }

    public void deleteSymlinks(File localFqDir) throws PipelineJobException
    {
        for (File fq : localFqDir.listFiles())
        {
            try
            {
                getLogger().debug("deleting symlink: " + fq.getName());
                Files.delete(fq.toPath());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    public static String getId(String idParam, Readset rs)
    {
        String id = FileUtil.makeLegalName(rs.getName()) + (idParam == null ? "" : "-" + idParam);
        id = id.replaceAll("[^a-zA-z0-9_\\-]", "_");

        return id;
    }
}
