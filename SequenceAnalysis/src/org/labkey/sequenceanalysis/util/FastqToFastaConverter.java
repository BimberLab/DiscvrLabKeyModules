package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.util.IOUtil;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

    public File execute(File output, List<File> inputs) throws PipelineJobException
    {
        try
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
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return output;
    }

    public File createInterleaved(File input1, File input2, File output) throws PipelineJobException
    {
        try
        {
            _logger.info("Converting FASTQs to interleaved FASTA");
            _logger.info("\tOutput: " + output.getPath());

            if (input1 == null || input2 == null)
            {
                throw new PipelineJobException("No FASTQ files supplied");
            }

            try (Writer writer = IOUtil.openFileForBufferedUtf8Writing(output))
            {
                _logger.info("\tprocessing files: " + input1.getPath());
                _logger.info("\tand: " + input2.getPath());

                try (FastqReader reader1 = new FastqReader(input1);FastqReader reader2 = new FastqReader(input2))
                {
                    int total = 0;
                    Boolean appendSuffix = null;
                    while ((reader1.hasNext()))
                    {
                        FastqRecord seq1 = reader1.next();
                        if (!reader2.hasNext())
                        {
                            throw new PipelineJobException("Inputs do not have the same number of sequences: " + input2.getPath());
                        }

                        FastqRecord seq2 = reader2.next();

                        if (appendSuffix == null)
                        {
                            appendSuffix = !seq1.getReadHeader().endsWith("/1");
                        }

                        SequenceUtil.writeFastaRecord(writer, seq1.getReadHeader() + (appendSuffix ? "/1" : ""), seq1.getReadString(), 60);
                        SequenceUtil.writeFastaRecord(writer, seq2.getReadHeader() + (appendSuffix ? "/2" : ""), seq2.getReadString(), 60);
                        total++;
                    }

                    if (reader2.hasNext())
                    {
                        throw new PipelineJobException("Inputs do not have the same number of sequences: " + input2.getPath());
                    }

                    _logger.info("\t" + total + " sequences");
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return output;
    }
}
