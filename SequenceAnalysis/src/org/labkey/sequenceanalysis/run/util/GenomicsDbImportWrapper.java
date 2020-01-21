package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class GenomicsDbImportWrapper extends AbstractGatk4Wrapper
{
    public GenomicsDbImportWrapper(Logger log)
    {
        super(log);
    }

    public void execute(ReferenceGenome genome, List<File> inputGvcfs, File outputFile, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running GATK 4 GenomicsDBImport");

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("GenomicsDBImport");

        inputGvcfs.forEach(f -> {
            args.add("-V");
            args.add(f.getPath());
        });

        args.add("--genomicsdb-workspace-path");
        args.add(outputFile.getPath());

        if (options != null)
        {
            args.addAll(options);
        }

        //NOTE: GenomicsDBImport requires explicit intervals
        File intervalList = new File(outputFile.getParentFile(), "intervals.list");
        try (PrintWriter writer = PrintWriters.getPrintWriter(intervalList))
        {
            SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
            for (SAMSequenceRecord rec : dict.getSequences())
            {
                writer.println(rec.getSequenceName());
            }
        }
        catch (FileNotFoundException e)
        {
            throw new PipelineJobException(e);
        }

        args.add("-L");
        args.add(intervalList.getPath());

        execute(args);

        if (!outputFile.exists())
        {
            throw new PipelineJobException("Unable to find expected file: " + outputFile.getPath());
        }
    }
}
