package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class BaseRecalibratorWrapper extends AbstractGatkWrapper
{
    public BaseRecalibratorWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, File inputBam, File outputBam, @Nullable File knownSitesVcf) throws PipelineJobException
    {
        getLogger().info("Running GATK BaseRecalibrator");

        ensureDictionary(referenceFasta);

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("-T");
        args.add("BaseRecalibrator");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());

        File recalFile = new File(outputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "recal_data.grp");
        args.add("-o");
        args.add(recalFile.getPath());

        args.add("--bam_compression");
        args.add("9");

        Integer maxThreads = SequenceTaskHelper.getMaxThreads(getLogger());
        if (maxThreads != null)
        {
            args.add("-nct");
            args.add(maxThreads.toString());
        }

        if (knownSitesVcf != null)
        {
            args.add("knownSites");
            args.add(knownSitesVcf.getPath());
        }

        execute(args);
        if (!recalFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + recalFile.getPath());
        }

        //then recalibrate the BAM
        getLogger().info("Running GATK PrintReads");

        List<String> printReadsArgs = new ArrayList<>(getBaseArgs());
        printReadsArgs.add("-T");
        printReadsArgs.add("PrintReads");
        printReadsArgs.add("-R");
        printReadsArgs.add(referenceFasta.getPath());
        printReadsArgs.add("-I");
        printReadsArgs.add(inputBam.getPath());
        printReadsArgs.add("-BQSR");
        printReadsArgs.add(recalFile.getPath());

        printReadsArgs.add("-o");
        printReadsArgs.add(outputBam.getPath());

        if (maxThreads != null)
        {
            printReadsArgs.add("-nct");
            printReadsArgs.add(maxThreads.toString());
        }

        execute(printReadsArgs);
    }
}
