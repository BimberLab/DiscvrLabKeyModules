package org.labkey.sequenceanalysis.run.util;

import com.drew.lang.annotations.Nullable;
import net.sf.picard.sam.BuildBamIndex;
import net.sf.samtools.SAMFileReader;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.run.AbstractCommandWrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 7/1/2014
 * Time: 2:40 PM
 */
public class IndelRealignerWrapper extends AbstractCommandWrapper
{
    public IndelRealignerWrapper(Logger log)
    {
        super(log);
    }

    public File execute(File inputBam, @Nullable File outputBam, File referenceFasta, @Nullable File knownSnpsVcf) throws PipelineJobException
    {
        getLogger().info("Running GATK IndelRealigner for: " + inputBam.getName());

        getLogger().info("\tensure dictionary exists");
        new CreateSequenceDictionaryWrapper(getLogger()).execute(referenceFasta, false);

        File expectedIndex = new File(inputBam.getPath() + ".bai");
        boolean doDeleteIndex = false;
        if (!expectedIndex.exists())
        {
            getLogger().debug("\tcreating temp index for BAM: " + inputBam.getName());
            try (SAMFileReader reader = new SAMFileReader(inputBam))
            {
                reader.setValidationStringency(SAMFileReader.ValidationStringency.SILENT);
                BuildBamIndex.createIndex(reader, expectedIndex);
            }
            doDeleteIndex = true;
        }

        getLogger().info("\tbuilding target intervals");
        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("-Xmx2g");
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("RealignerTargetCreator");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-o");

        File intervalsFile = getExpectedIntervalsFile(inputBam);
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
        realignerArgs.add("-Xmx2g");
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

        if (doDeleteIndex)
        {
            getLogger().debug("\tdeleting temp BAM index: " + expectedIndex.getPath());
            expectedIndex.delete();
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

    private File getJAR()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("PICARDPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("GenomeAnalysisTK.jar") : new File(path, "GenomeAnalysisTK.jar");
    }

    public boolean jarExists()
    {
        return getJAR() == null || !getJAR().exists();
    }
}
