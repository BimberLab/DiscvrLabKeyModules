package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 11/20/12
 * Time: 9:49 PM
 */
public class SequenceUtil
{
    public static FILETYPE inferType(File file)
    {
        for (FILETYPE f : FILETYPE.values())
        {
            FileType ft = f.getFileType();
            if (ft.isType(file))
                return f;
        }
        return null;
    }

    public static enum FILETYPE
    {
        fastq(".fastq", ".fq"),
        fasta(".fasta"),
        sff(".sff");

        List<String> _extensions;

        FILETYPE(String... extensions)
        {
            _extensions = Arrays.asList(extensions);
        }

        public FileType getFileType()
        {
            return new FileType(_extensions, _extensions.get(0));
        }

        public String getPrimaryExtension()
        {
            return _extensions.get(0);
        }
    }

    public static long getLineCount(File f) throws PipelineJobException
    {
        try (BufferedReader reader = new BufferedReader(new FileReader(f)))
        {
            long i = 0;
            while (reader.readLine() != null)
            {
                i++;
            }

            return i;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public static long getAlignmentCount(File bam)
    {
        try (SAMFileReader reader = new SAMFileReader(bam))
        {
            reader.setValidationStringency(ValidationStringency.SILENT);

            try (SAMRecordIterator it = reader.iterator())
            {
                long count = 0;
                while (it.next() != null)
                {
                    count++;
                }

                return count;
            }
        }
    }

    public static void writeFastaRecord(Writer writer, String header, String sequence, int lineLength) throws IOException
    {
        writer.write(">" + header + "\n");
        if (sequence != null)
        {
            int len = sequence.length();
            for (int i=0; i<len; i+=lineLength)
            {
                writer.write(sequence.substring(i, Math.min(len, i + lineLength)) + "\n");
            }
        }
    }

    public static void bgzip(File input, File output)
    {
        try (FileInputStream i = new FileInputStream(input); BlockCompressedOutputStream o = new BlockCompressedOutputStream(new FileOutputStream(output), output))
        {
            FileUtil.copyData(i, o);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static SAMFileHeader.SortOrder getBamSortOrder(File bam)
    {
        try (SAMFileReader reader = new SAMFileReader(bam))
        {
            return reader.getFileHeader().getSortOrder();
        }
    }

    public static void logFastqBamDifferences(Logger log, File bam, Integer fastqReadCount, @Nullable Integer fastq2ReadCount)
    {
        int totalFirstMateAlignments = 0;
        int totalFirstMatePrimaryAlignments = 0;

        int totalSecondMateAlignments = 0;
        int totalSecondMatePrimaryAlignments = 0;

        try (SAMFileReader reader = new SAMFileReader(bam))
        {
            reader.setValidationStringency(ValidationStringency.SILENT);

            try (SAMRecordIterator it = reader.iterator())
            {
                while (it.hasNext())
                {
                    SAMRecord r = it.next();
                    if (r.getReadUnmappedFlag())
                    {
                        continue;
                    }

                    //count all alignments
                    if (r.getReadPairedFlag() && r.getSecondOfPairFlag())
                    {
                        totalSecondMateAlignments++;
                    }
                    else
                    {
                        totalFirstMateAlignments++;
                    }

                    //also just primary alignments
                    if (!r.isSecondaryOrSupplementary())
                    {
                        if (r.getReadPairedFlag() && r.getSecondOfPairFlag())
                        {
                            totalSecondMatePrimaryAlignments++;
                        }
                        else
                        {
                            totalFirstMatePrimaryAlignments++;
                        }
                    }
                }

                log.info("Total first mate alignments: " + totalFirstMateAlignments);
                log.info("Total first second mate alignments: " + totalSecondMateAlignments);

                log.info("Total first mate primary alignments: " + totalFirstMatePrimaryAlignments);
                if (fastqReadCount != null)
                {
                    log.info("\tDifference from FASTQ: " + (fastqReadCount - totalFirstMatePrimaryAlignments));
                }

                log.info("Total second mate primary alignments: " + totalSecondMatePrimaryAlignments);
                if (fastq2ReadCount != null)
                {
                    log.info("\tDifference from second FASTQ: " + (fastq2ReadCount - totalSecondMatePrimaryAlignments));
                }
            }
        }
    }
}
