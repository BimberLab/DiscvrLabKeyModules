package org.labkey.sequenceanalysis.util;

import net.sf.picard.fastq.FastqReader;
import net.sf.picard.fastq.FastqRecord;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.List;

/**
 * User: bimber
 * Date: 11/23/12
 * Time: 9:41 PM
 */
public class FastqToFastaConverter
{
    private Logger _logger;

    public FastqToFastaConverter(Logger logger)
    {
        _logger = logger;
    }

    public File execute(File output, List<File> inputs) throws Exception
    {
        _logger.info("Converting FASTQ(s) to FASTA");
        _logger.info("\tOutput: " + output.getPath());

        if (inputs == null || inputs.isEmpty())
        {
            throw new PipelineJobException("No FASTQ files supplied");
        }

        if (!output.exists())
        {
            if (!output.getParentFile().exists())
                output.getParentFile().mkdirs();

            output.createNewFile();
        }

        try (Writer writer = new BufferedWriter(new FileWriter(output)))
        {
            for (File input : inputs)
            {
                _logger.info("\tprocessing file: " + input.getPath());
                try (FastqReader reader = new FastqReader(input))
                {
                    int total = 0;
                    while ((reader.hasNext()))
                    {
                        FastqRecord seq = reader.next();
                        SequenceUtil.writeFastaRecord(writer, seq.getReadHeader(), seq.getReadString(), 60);
                        total++;
                    }

                    reader.close();

                    _logger.info("\t" + total + " sequences");
                }
            }
        }

        return output;
    }
}
