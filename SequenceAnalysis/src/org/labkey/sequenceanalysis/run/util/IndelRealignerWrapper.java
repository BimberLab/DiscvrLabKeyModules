package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.resource.FileResource;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 7/1/2014
 * Time: 2:40 PM
 */
public class IndelRealignerWrapper extends AbstractGatkWrapper
{
    public IndelRealignerWrapper(Logger log)
    {
        super(log);
    }

    public File execute(File inputBam, @Nullable File outputBam, File referenceFasta, @Nullable File knownIndelsVcf) throws PipelineJobException
    {
        getLogger().info("Running GATK IndelRealigner for: " + inputBam.getName());

        List<File> tempFiles = new ArrayList<>();
        File workingBam = performSharedWork(inputBam, outputBam, referenceFasta, tempFiles);
        if (!workingBam.equals(inputBam))
        {
            tempFiles.add(workingBam);
        }

        File intervalsFile = buildTargetIntervals(referenceFasta, workingBam, knownIndelsVcf, getExpectedIntervalsFile(inputBam));
        if (intervalsFile == null)
        {
            getLogger().info("no intervals to realign, skipping");
            return processOutput(tempFiles, inputBam, outputBam, null);
        }

        //then run realigner
        getLogger().info("\trunning IndelRealigner");
        List<String> realignerArgs = new ArrayList<>(getBaseArgs());
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

    public File executeWithQueue(File inputBam, File outputBam, File referenceFasta, @Nullable File knownIndelsVcf) throws PipelineJobException
    {
        getLogger().info("Running GATK IndelRealigner using Queue for: " + inputBam.getName());
        addJavaHomeToEnvironment();

        List<File> tempFiles = new ArrayList<>();
        File workingBam = performSharedWork(inputBam, outputBam, referenceFasta, tempFiles);
        if (!workingBam.equals(inputBam))
        {
            tempFiles.add(workingBam);
        }

        File intervalsFile = buildTargetIntervals(referenceFasta, workingBam, knownIndelsVcf, getExpectedIntervalsFile(inputBam));
        if (intervalsFile == null)
        {
            getLogger().info("no intervals to realign, skipping");
            return processOutput(tempFiles, inputBam, outputBam, null);
        }

        try
        {
            Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
            FileResource r = (FileResource)module.getModuleResolver().lookup(Path.parse("external/qscript/IndelRealignerRunner.scala"));
            File scalaScript = r.getFile();

            if (scalaScript == null)
                throw new FileNotFoundException("Not found: " + scalaScript);

            if (!scalaScript.exists())
                throw new FileNotFoundException("Not found: " + scalaScript.getPath());

            List<String> args = new ArrayList<>();
            args.add(SequencePipelineService.get().getJava8FilePath());
            //for now, ignore java opts since queue's scatter/gather causes issues
            //args.addAll(SequencePipelineService.get().getJavaOpts());
            args.add("-classpath");
            args.add(getJAR().getPath());
            args.addAll(SequencePipelineService.get().getJavaOpts());
            args.add("-jar");
            args.add(getQueueJAR().getPath());
            args.add("-S");
            args.add(scalaScript.getPath());
            args.add("-jobRunner");
            args.add("ParallelShell");
            args.add("-run");

            args.add("-R");
            args.add(referenceFasta.getPath());
            args.add("-I");
            args.add(workingBam.getPath());
            args.add("-targetIntervals");
            args.add(intervalsFile.getPath());

            args.add("-runDir");
            args.add(outputBam.getParentFile().getPath());

            String tmpDir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_TMP_DIR");
            if (StringUtils.trimToNull(tmpDir) != null)
            {
                args.add("-tempDir");
                args.add(tmpDir);
            }

            args.add("-o");

            File realignedBam = outputBam == null ? new File(getOutputDir(inputBam), FileUtil.getBaseName(inputBam) + ".realigned.bam") : outputBam;
            args.add(realignedBam.getPath());

            args.add("-startFromScratch");
            args.add("-scatterCount");
            args.add(getScatterForQueueJob().toString());

            execute(args);
            if (!realignedBam.exists())
            {
                throw new PipelineJobException("Expected output not found: " + realignedBam.getPath());
            }

            return processOutput(tempFiles, inputBam, outputBam, realignedBam);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
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

        if (realignedBam == null)
        {
            return null;
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

    private File buildTargetIntervals(File referenceFasta, File inputBam, File knownIndelsVcf, File intervalsFile) throws PipelineJobException
    {
        getLogger().info("building target intervals");
        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("-T");
        args.add("RealignerTargetCreator");
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

        Integer maxThreads = SequenceTaskHelper.getMaxThreads(getLogger());
        if (maxThreads != null)
        {
            args.add("-nt");
            args.add(maxThreads.toString());
        }

        execute(args);

        //log the intervals
        long lineCount = SequenceUtil.getLineCount(intervalsFile);
        getLogger().info("\ttarget intervals to realign: " + lineCount);

        return lineCount == 0 ? null : intervalsFile;
    }

    private File performSharedWork(File inputBam, File outputBam, File referenceFasta, List<File> tempFiles) throws PipelineJobException
    {
        //ensure BAM sorted
        try
        {
            SAMFileHeader.SortOrder order = SequenceUtil.getBamSortOrder(inputBam);

            if (SAMFileHeader.SortOrder.coordinate != order)
            {
                getLogger().info("coordinate sorting BAM, order was: " + (order == null ? "not provided" : order.name()));
                File sorted = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + ".sorted.bam");
                new SamSorter(getLogger()).execute(inputBam, sorted, SAMFileHeader.SortOrder.coordinate);

                //this indicates we expect to replace the original in place, in which case we should delete the unsorted BAM
                if (outputBam == null)
                {
                    tempFiles.add(inputBam);
                    File idx = new File(inputBam.getPath() + ".bai");
                    if (idx.exists())
                    {
                        tempFiles.add(idx);
                    }
                }
                else
                {
                    tempFiles.add(sorted);

                    File idx = new File(sorted.getPath() + ".bai");
                    if (idx.exists())
                    {
                        tempFiles.add(idx);
                    }
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

        File expectedIndex = new File(inputBam.getPath() + ".bai");
        if (expectedIndex.exists() && expectedIndex.lastModified() < inputBam.lastModified())
        {
            getLogger().info("deleting out of date index: " + expectedIndex.getPath());
            expectedIndex.delete();
        }

        if (!expectedIndex.exists())
        {
            getLogger().debug("\tcreating temp index for BAM: " + inputBam.getName());
            BuildBamIndexWrapper buildBamIndexWrapper = new BuildBamIndexWrapper(getLogger());
            buildBamIndexWrapper.setStringency(ValidationStringency.SILENT);
            buildBamIndexWrapper.executeCommand(inputBam);
            tempFiles.add(expectedIndex);
        }
        else
        {
            getLogger().debug("BAM index already exists: " + expectedIndex.getPath());
        }

        return inputBam;
    }

    public File getExpectedIntervalsFile(File inputBam)
    {
        return new File(getOutputDir(inputBam), FileUtil.getBaseName(inputBam) + ".intervals");
    }
}
