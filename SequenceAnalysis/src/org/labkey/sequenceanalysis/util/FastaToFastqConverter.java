package org.labkey.sequenceanalysis.util;

import net.sf.picard.fastq.FastqRecord;
import net.sf.picard.fastq.FastqWriter;
import net.sf.picard.fastq.FastqWriterFactory;
import net.sf.picard.reference.FastaSequenceFile;
import net.sf.picard.reference.ReferenceSequence;
import org.apache.log4j.Logger;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
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
        FastaSequenceFile fasta = null;
        FastqWriter writer = null;
        FastqWriterFactory fact = new FastqWriterFactory();

        _logger.info("Converting FASTA to FASTQ: " + input.getPath());

        try
        {
            fasta = new FastaSequenceFile(input, false);
            writer = fact.newWriter(output);

            ReferenceSequence seq;
            int total = 0;
            while ((seq = fasta.nextSequence()) != null)
            {
                String qual = "";
                String nt = "";
                String c = String.valueOf(Character.toChars(_defaultQual)[0]);

                for (int i = 0; i <= seq.getBases().length; i++) {
                    qual.concat(c);
                    nt.concat(Character.toString((char)seq.getBases()[i]));
                }

                FastqRecord rec = new FastqRecord(seq.getName(), nt, null, qual);
                writer.write(rec);
                total++;
            }

            _logger.info("\t" + total + " sequences");
        }
        finally
        {
            if (writer != null)
                writer.close();

            if (fasta != null)
                fasta.close();
        }

        return output;
    }
}
