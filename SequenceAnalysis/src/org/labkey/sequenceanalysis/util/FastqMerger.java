package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Iterator;
import java.util.List;

/**
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
        fact.setUseAsyncIo(true);

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
