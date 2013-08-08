package org.labkey.sequenceanalysis.util;

import net.sf.picard.fastq.FastqReader;
import net.sf.picard.fastq.FastqRecord;
import net.sf.picard.fastq.FastqWriter;
import net.sf.picard.fastq.FastqWriterFactory;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import org.apache.log4j.Logger;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/5/12
 * Time: 12:13 PM
 */
public class CreateUnalignedFastq
{
    Logger _logger;

    public CreateUnalignedFastq(Logger logger)
    {
        _logger = logger;
    }

    public File execute(File bam, List<Pair<File, File>> inputFastqs)
    {
        SAMFileReader reader = null;
        FastqWriter fqWriter = null;
        FastqWriterFactory fact = new FastqWriterFactory();

        try
        {
            reader = new SAMFileReader(bam);
            File unalignedFastq = new File(bam.getParentFile(), FileUtil.getBaseName(bam) + ".unaligned.fastq");
            fqWriter = fact.newWriter(unalignedFastq);

            Set<String> forward = new HashSet<>();
            Set<String> reverse = new HashSet<>();

            buildSequenceSet(reader, forward, reverse);

            for (Pair<File, File> pair : inputFastqs)
            {
                if (pair.first != null)
                    appendFastq(fqWriter, pair.first, forward, reverse);

                if (pair.second != null)
                    appendFastq(fqWriter, pair.second, forward, reverse);
            }

            return unalignedFastq;
        }
        finally
        {
            if (reader != null)
                reader.close();

            if (fqWriter != null)
                fqWriter.close();
        }
    }

    private void buildSequenceSet(SAMFileReader reader, Set<String> forward, Set<String> reverse)
    {
        Iterator<SAMRecord> iterator = reader.iterator();
        while (iterator.hasNext())
        {
            SAMRecord record = iterator.next();

            if (record.getSecondOfPairFlag())
                reverse.add(record.getReadName());

            if (!record.getReadPairedFlag() || record.getFirstOfPairFlag())
                forward.add(record.getReadName());
        }
    }

    private void appendFastq(FastqWriter fqWriter, File fastq, Set<String> forward, Set<String> reverse)
    {
        FastqReader reader = null;
        try
        {
            reader = new FastqReader(fastq);
            Iterator<FastqRecord> iterator = reader.iterator();
            while (iterator.hasNext())
            {
                FastqRecord record = iterator.next();
                if (!forward.contains(record.getReadHeader()))
                {
                    fqWriter.write(record);
                }
            }

        }
        finally
        {
            if (reader != null)
                reader.close();
        }

    }
}
