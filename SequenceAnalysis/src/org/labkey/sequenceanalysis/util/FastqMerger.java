package org.labkey.sequenceanalysis.util;

import net.sf.picard.fastq.FastqReader;
import net.sf.picard.fastq.FastqRecord;
import net.sf.picard.fastq.FastqWriter;
import net.sf.picard.fastq.FastqWriterFactory;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Iterator;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/20/12
 * Time: 10:27 PM
 */
public class FastqMerger
{
    private Logger _logger;

    public FastqMerger(Logger logger)
    {
        _logger = logger;
    }

    public void mergeFiles(File output, List<File> inputs)
    {
        _logger.info("Merging FASTQ Files:");
        for (File f : inputs)
        {
            _logger.info(f.getName());
        }

        FastqWriter writer = null;
        FastqReader reader = null;
        FastqWriterFactory fact = new FastqWriterFactory();

        try
        {
            writer = fact.newWriter(output);

            for (File input : inputs)
            {
                reader = new FastqReader(input);
                Iterator<FastqRecord> iterator = reader.iterator();

                while (iterator.hasNext())
                {
                    writer.write(iterator.next());
                }
                reader.close();
                reader = null;
            }
        }
        finally
        {
            if (writer != null)
                writer.close();

            if (reader != null)
                reader.close();
        }
    }
}
