package org.labkey.sequenceanalysis.run.util;

import com.drew.lang.annotations.Nullable;
import htsjdk.samtools.BAMIndexer;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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

    public File execute(File inputBam, @Nullable File outputBam, File referenceFasta, @Nullable File knownSnpsVcf) throws PipelineJobException
    {
        getLogger().info("Running GATK IndelRealigner for: " + inputBam.getName());

        File intervalsFile = getExpectedIntervalsFile(inputBam);
        List<File> tempFiles = new ArrayList<>();

        //ensure BAM sorted
        SAMFileHeader.SortOrder order = SequenceUtil.getBamSortOrder(inputBam);
        if (SAMFileHeader.SortOrder.coordinate != order)
        {
            getLogger().info("coordinate sorting BAM, order was: " + (order == null ? "not provided" : order.name()));
            File sorted = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + ".sorted.bam");
            new SortSamWrapper(getLogger()).execute(inputBam, sorted, SAMFileHeader.SortOrder.coordinate);

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

        ensureDictionary(referenceFasta);

        File expectedIndex = new File(inputBam.getPath() + ".bai");
        if (!expectedIndex.exists())
        {
            getLogger().debug("\tcreating temp index for BAM: " + inputBam.getName());
            try (SAMFileReader reader = new SAMFileReader(inputBam))
            {
                reader.setValidationStringency(ValidationStringency.SILENT);
                BAMIndexer.createIndex(reader, expectedIndex);
            }
            tempFiles.add(expectedIndex);
        }

        getLogger().info("\tbuilding target intervals");
        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("-Xmx4g");
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("RealignerTargetCreator");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-o");

        args.add(intervalsFile.getPath());

        if (knownSnpsVcf != null)
        {
            args.add("--known");
            args.add(knownSnpsVcf.getPath());
        }

        execute(args);

        //log the intervals
        getLogger().info("\ttarget intervals:");
        try (BufferedReader reader = new BufferedReader(new FileReader(intervalsFile)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                getLogger().info("\t" + line);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        //then run realigner
        getLogger().info("\trunning IndelRealigner");
        List<String> realignerArgs = new ArrayList<>();
        realignerArgs.add("java");
        realignerArgs.add("-Xmx4g");
        realignerArgs.add("-jar");
        realignerArgs.add(getJAR().getPath());
        realignerArgs.add("-T");
        realignerArgs.add("IndelRealigner");
        realignerArgs.add("-R");
        realignerArgs.add(referenceFasta.getPath());
        realignerArgs.add("-I");
        realignerArgs.add(inputBam.getPath());
        realignerArgs.add("-o");

        File realignedBam = outputBam == null ? new File(getOutputDir(inputBam), FileUtil.getBaseName(inputBam) + ".realigned.bam") : outputBam;
        realignerArgs.add(realignedBam.getPath());
        realignerArgs.add("-targetIntervals");
        realignerArgs.add(intervalsFile.getPath());
        if (knownSnpsVcf != null)
        {
            realignerArgs.add("--known");
            realignerArgs.add(knownSnpsVcf.getPath());
        }

        execute(realignerArgs);
        if (!realignedBam.exists())
        {
            throw new PipelineJobException("Expected BAM not found: " + realignedBam.getPath());
        }

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

    public File getExpectedIntervalsFile(File inputBam)
    {
        return new File(getOutputDir(inputBam), FileUtil.getBaseName(inputBam) + ".intervals");
    }
}
