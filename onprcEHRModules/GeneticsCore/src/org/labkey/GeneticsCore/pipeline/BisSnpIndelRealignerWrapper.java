package org.labkey.GeneticsCore.pipeline;

import htsjdk.samtools.SAMFileHeader;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 7/1/2014
 * Time: 2:40 PM
 */
public class BisSnpIndelRealignerWrapper extends AbstractBisSnpWrapper
{
    public BisSnpIndelRealignerWrapper(Logger log)
    {
        super(log);
    }

    public File execute(File inputBam, @Nullable File outputBam, File referenceFasta, @Nullable File knownIndelsVcf, @Nullable Integer maxThreads) throws PipelineJobException
    {
        getLogger().info("Running BisSNP IndelRealigner for: " + inputBam.getName());

        List<File> tempFiles = new ArrayList<>();
        File workingBam = performSharedWork(inputBam, outputBam, referenceFasta, tempFiles);
        if (!workingBam.equals(inputBam))
        {
            tempFiles.add(workingBam);
        }

        List<String> extraArgs = new ArrayList<>();
        if (maxThreads != null)
        {
            extraArgs.add("-nt");
            extraArgs.add(maxThreads.toString());
        }

        File intervalsFile = buildTargetIntervals(referenceFasta, workingBam, knownIndelsVcf, getExpectedIntervalsFile(inputBam), extraArgs);

        //then run realigner
        getLogger().info("\trunning BisSNP IndelRealigner");
        List<String> realignerArgs = new ArrayList<>();
        realignerArgs.add(getJava6Filepath());
        realignerArgs.addAll(SequencePipelineService.get().getJavaOpts());
        realignerArgs.add("-jar");
        realignerArgs.add(getJAR().getPath());
        realignerArgs.add("-T");
        realignerArgs.add("IndelRealigner");
        realignerArgs.add("-R");
        realignerArgs.add(referenceFasta.getPath());
        realignerArgs.add("-I");
        realignerArgs.add(workingBam.getPath());
        realignerArgs.add("-o");

        File realignedBam = outputBam == null ? new File(getOutputDir(inputBam), FileUtil.getBaseName(inputBam) + ".realigned.bam") : outputBam;
        realignerArgs.add(realignedBam.getPath());
        realignerArgs.add("-targetIntervals");
        realignerArgs.add(intervalsFile.getPath());
        realignerArgs.add("--bam_compression");
        realignerArgs.add("9");
        if (knownIndelsVcf != null)
        {
            realignerArgs.add("--known");
            realignerArgs.add(knownIndelsVcf.getPath());
        }

        execute(realignerArgs);
        if (!realignedBam.exists())
        {
            throw new PipelineJobException("Expected BAM not found: " + realignedBam.getPath());
        }

        return processOutput(tempFiles, inputBam, outputBam, realignedBam);
    }

    private File processOutput(List<File> tempFiles, File inputBam, File outputBam, File realignedBam) throws PipelineJobException
    {
        if (!tempFiles.isEmpty())
        {
            for (File f : tempFiles)
            {
                getLogger().debug("\tdeleting temp file: " + f.getPath());
                f.delete();
            }
        }

        try
        {
            if (outputBam == null)
            {
                getLogger().debug("replacing input BAM with realigned");
                inputBam.delete();
                FileUtils.moveFile(realignedBam, inputBam);

                return inputBam;
            }
            else
            {
                return realignedBam;
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private File buildTargetIntervals(File referenceFasta, File inputBam, File knownIndelsVcf, File intervalsFile, @Nullable List<String> extraArgs) throws PipelineJobException
    {
        getLogger().info("building target intervals");
        List<String> args = new ArrayList<>();
        args.add(getJava6Filepath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("BisulfiteRealignerTargetCreator");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-o");

        args.add(intervalsFile.getPath());

        if (knownIndelsVcf != null)
        {
            args.add("--known");
            args.add(knownIndelsVcf.getPath());
        }

        if (extraArgs != null)
        {
            args.addAll(extraArgs);
        }

        execute(args);

        //log the intervals
        long lineCount = SequencePipelineService.get().getLineCount(intervalsFile);
        getLogger().info("\ttarget intervals to realign: " + lineCount);

        return intervalsFile;
    }

    private File performSharedWork(File inputBam, File outputBam, File referenceFasta, List<File> tempFiles) throws PipelineJobException
    {
        //ensure BAM sorted
        try
        {
            SAMFileHeader.SortOrder order = SequencePipelineService.get().getBamSortOrder(inputBam);

            if (SAMFileHeader.SortOrder.coordinate != order)
            {
                getLogger().info("coordinate sorting BAM, order was: " + (order == null ? "not provided" : order.name()));
                File sorted = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + ".sorted.bam");
                new SamSorter(getLogger()).execute(inputBam, sorted, SAMFileHeader.SortOrder.coordinate);

                //this indicates we expect to replace the original in place, in which case we should delete the unsorted BAM
                if (outputBam == null)
                {
                    tempFiles.add(inputBam);
                }

                inputBam = sorted;
            }
            else
            {
                getLogger().info("bam is already in coordinate sort order");
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        ensureDictionary(referenceFasta);

        File idx = SequencePipelineService.get().ensureBamIndex(inputBam, getLogger(), true);
        if (idx != null)
            tempFiles.add(idx);

        return inputBam;
    }

    public File getExpectedIntervalsFile(File inputBam)
    {
        return new File(getOutputDir(inputBam), FileUtil.getBaseName(inputBam) + ".intervals");
    }
}
