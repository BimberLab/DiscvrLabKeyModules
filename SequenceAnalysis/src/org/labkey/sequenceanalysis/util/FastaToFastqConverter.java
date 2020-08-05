package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import htsjdk.samtools.reference.FastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.File;

/**
 * User: bimber
 * Date: 11/23/12
 * Time: 9:41 PM
 */
public class FastaToFastqConverter
{
    private Logger _logger;
    private int _defaultQual = 80;

    public FastaToFastqConverter(Logger logger)
    {
        _logger = logger;
    }

    public File execute(File input, File output)
    {
        FastqWriterFactory fact = new FastqWriterFactory();
        fact.setUseAsyncIo(true);
        _logger.info("Converting FASTA to FASTQ: " + input.getPath());

        try (FastaSequenceFile fasta = new FastaSequenceFile(input, false);FastqWriter writer = fact.newWriter(output))
        {
            ReferenceSequence seq;
            int total = 0;
            while ((seq = fasta.nextSequence()) != null)
            {
                StringBuilder qual = new StringBuilder();
                StringBuilder nt = new StringBuilder();
                String c = String.valueOf(Character.toChars(_defaultQual)[0]);

                for (int i = 0; i < seq.getBases().length; i++) {
                    qual.append(c);
                    nt.append(Character.toString((char)seq.getBases()[i]));
                }

                FastqRecord rec = new FastqRecord(seq.getName(), nt.toString(), null, qual.toString());
                writer.write(rec);
                total++;
            }

            _logger.info("\t" + total + " sequences");
        }

        return output;
    }
}
