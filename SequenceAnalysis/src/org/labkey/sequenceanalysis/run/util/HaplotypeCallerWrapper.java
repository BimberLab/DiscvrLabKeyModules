package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.BAMIndexer;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class HaplotypeCallerWrapper extends AbstractGatkWrapper
{
    public HaplotypeCallerWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File inputBam, File referenceFasta, File outputFile, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running GATK HaplotypeCaller for: " + inputBam.getName());

        ensureDictionary(referenceFasta);

        File expectedIndex = new File(inputBam.getPath() + ".bai");
        boolean doDeleteIndex = false;
        if (!expectedIndex.exists())
        {
            getLogger().debug("\tcreating temp index for BAM: " + inputBam.getName());
            //TODO: SamReaderFactory fact = SamReaderFactory.make();
            try (SAMFileReader reader = new SAMFileReader(inputBam))
            {
                reader.setValidationStringency(ValidationStringency.SILENT);
                BAMIndexer.createIndex(reader, expectedIndex);
            }

            doDeleteIndex = true;
        }

        getLogger().info("\tbuilding target intervals");
        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("-Xmx4g");
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("HaplotypeCaller");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-o");
        args.add(outputFile.getPath());
        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
        }

        if (doDeleteIndex)
        {
            getLogger().debug("\tdeleting temp BAM index: " + expectedIndex.getPath());
            expectedIndex.delete();
        }
    }
}
