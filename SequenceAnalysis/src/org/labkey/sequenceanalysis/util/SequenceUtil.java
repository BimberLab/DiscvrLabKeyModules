package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.CommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.run.util.BuildBamIndexWrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

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
        fastq(Arrays.asList(".fastq", ".fq"), true),
        fasta(Arrays.asList("fasta", "fa"), true),
        bam(".bam"),
        sff(".sff"),
        gtf(".gtf"),
        gff(".gff"),
        bed(".bed"),
        vcf(Arrays.asList(".vcf"), true);

        private List<String> _extensions;
        private boolean _allowGzip;

        FILETYPE(String extension)
        {
            this(Arrays.asList(extension), false);
        }

        FILETYPE(List<String> extensions, boolean allowGzip)
        {
            _extensions = extensions;
            _allowGzip = allowGzip;
        }

        public FileType getFileType()
        {
            return new FileType(_extensions, _extensions.get(0), false, _allowGzip ? FileType.gzSupportLevel.SUPPORT_GZ : FileType.gzSupportLevel.NO_GZ);
        }

        public String getPrimaryExtension()
        {
            return _extensions.get(0);
        }
    }

    public static long getLineCount(File f) throws PipelineJobException
    {
        FileType gz = new FileType(".gz");
        try (InputStream is = gz.isType(f) ? new GZIPInputStream(new FileInputStream(f)) : new FileInputStream(f);BufferedReader reader = new BufferedReader(new InputStreamReader(is));)
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

    public static boolean hasLineCount(File f) throws PipelineJobException
    {
        return hasMinLineCount(f, 0);
    }

    public static boolean hasMinLineCount(File f, long minLines) throws PipelineJobException
    {
        FileType gz = new FileType(".gz");
        try (InputStream is = gz.isType(f) ? new GZIPInputStream(new FileInputStream(f)) : new FileInputStream(f);BufferedReader reader = new BufferedReader(new InputStreamReader(is));)
        {
            long lineNo = 0;
            while (reader.readLine() != null)
            {
                lineNo++;
                if (lineNo >= minLines)
                {
                    return true;
                }
            }

            return false;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public static long getAlignmentCount(File bam) throws IOException
    {
        SamReaderFactory fact = SamReaderFactory.makeDefault();
        fact.validationStringency(ValidationStringency.SILENT);
        try (SamReader reader = fact.open(bam))
        {
            try (SAMRecordIterator it = reader.iterator())
            {
                long count = 0;
                while (it.hasNext())
                {
                    it.next();
                    count++;
                }

                return count;
            }
        }
    }

    public static void logAlignmentCount(File bam, Logger log) throws IOException
    {
        SamReaderFactory fact = SamReaderFactory.makeDefault();
        fact.validationStringency(ValidationStringency.SILENT);
        try (SamReader reader = fact.open(bam))
        {
            try (SAMRecordIterator it = reader.iterator())
            {
                long total = 0;
                long primary = 0;
                long unaligned = 0;
                while (it.hasNext())
                {
                    SAMRecord r = it.next();
                    total++;

                    if (r.getReadUnmappedFlag())
                    {
                        unaligned++;
                    }
                    else if (!r.isSecondaryOrSupplementary())
                    {
                        primary++;
                    }
                }

                log.info("file size: " + FileUtils.byteCountToDisplaySize(bam.length()));
                log.info("total alignments: " + total);
                log.info("primary alignments: " + primary);
                log.info("unaligned: " + unaligned);
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

    public static SAMFileHeader.SortOrder getBamSortOrder(File bam) throws IOException
    {
        SamReaderFactory fact = SamReaderFactory.makeDefault();
        try (SamReader reader = fact.open(bam))
        {
            return reader.getFileHeader().getSortOrder();
        }
    }

    public static void logFastqBamDifferences(Logger log, File bam) throws IOException
    {
        final long bytes = 10737418240L; //10gb
        long size1 = FileUtils.sizeOf(bam);
        if (size1 > bytes)
        {
            log.info("File size: " + FileUtils.byteCountToDisplaySize(size1));
        }
        else
        {
            int totalFirstMateAlignments = 0;
            int totalFirstMatePrimaryAlignments = 0;

            int totalSecondMateAlignments = 0;
            int totalSecondMatePrimaryAlignments = 0;

            SamReaderFactory fact = SamReaderFactory.makeDefault();
            fact.validationStringency(ValidationStringency.SILENT);
            try (SamReader reader = fact.open(bam))
            {
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

                    log.info("File size: " + FileUtils.byteCountToDisplaySize(bam.length()));
                    log.info("Total first mate alignments: " + totalFirstMateAlignments);
                    log.info("Total first second mate alignments: " + totalSecondMateAlignments);

                    log.info("Total first mate primary alignments: " + totalFirstMatePrimaryAlignments);

                    log.info("Total second mate primary alignments: " + totalSecondMatePrimaryAlignments);
                }
            }
        }
    }

    public static List<Interval> bedToIntervalList(File input) throws IOException
    {
        List<Interval> ret = new ArrayList<>();

        try (FeatureReader<BEDFeature> reader = AbstractFeatureReader.getFeatureReader(input.getAbsolutePath(), new BEDCodec(), false))
        {
            try (CloseableTribbleIterator<BEDFeature> i = reader.iterator())
            {
                while (i.hasNext())
                {
                    BEDFeature f = i.next();
                    ret.add(new Interval(f.getChr(), f.getStart(), f.getEnd()));
                }
            }
        }

        return ret;
    }

    public static JSONArray getReadGroupsForBam(File bam) throws IOException
    {
        if (bam == null || !bam.exists())
        {
            return null;
        }

        JSONArray ret = new JSONArray();

        SamReaderFactory fact = SamReaderFactory.makeDefault();
        try (SamReader reader = fact.open(bam))
        {
            SAMFileHeader header = reader.getFileHeader();
            if (header != null)
            {
                List<SAMReadGroupRecord> groups = header.getReadGroups();
                for (SAMReadGroupRecord g : groups)
                {
                    JSONObject details = new JSONObject();
                    details.put("platform", g.getPlatform());
                    details.put("platformUnit", g.getPlatformUnit());
                    details.put("description", g.getDescription());
                    details.put("sample", g.getSample());
                    details.put("id", g.getId());
                    details.put("date", g.getRunDate());
                    details.put("readGroupId", g.getReadGroupId());
                    details.put("centerName", g.getSequencingCenter());

                    ret.put(details);
                }
            }
        }

        return ret;
    }

    public static void deleteBamAndIndex(File bam)
    {
        bam.delete();

        File idx = new File(bam.getPath() + ".bai");
        if (idx.exists())
        {
            idx.delete();
        }
    }

    public static void recreateOldBamIndex(File bam, boolean forceRecreate, @Nullable Logger log) throws PipelineJobException
    {
        File idx = new File(bam.getPath() + ".bai");

        //delete out of date index
        if (idx.exists() && (forceRecreate || idx.lastModified() < bam.lastModified()))
        {
            if (log != null)
                log.info("deleting existing BAM index");

            idx.delete();
        }

        if (!idx.exists())
        {
            new BuildBamIndexWrapper(log).executeCommand(bam);
        }
    }

    public static void sortROD(File input, Logger log) throws IOException, PipelineJobException
    {
        log.info("sorting file: " + input.getPath());

        boolean isCompressed = input.getPath().endsWith(".gz");
        File sorted = new File(input.getParent(), "sorted.tmp");
        try (PrintWriter writer = PrintWriters.getPrintWriter(sorted))
        {
            //copy header
            try (BufferedReader reader = IOUtil.openFileForBufferedUtf8Reading(input))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.startsWith("#"))
                    {
                        writer.write(line + '\n');
                    }
                    else
                    {
                        //the first non-comment line means we left the header
                        break;
                    }
                }
            }

            //then sort/append the records
            CommandWrapper wrapper = SequencePipelineService.get().getCommandWrapper(log);
            wrapper.execute(Arrays.asList("/bin/sh", "-c", "cat '" + input.getPath() + "' | grep -v '^#' | sort -k1,1 -k2,2n"), ProcessBuilder.Redirect.appendTo(sorted));
        }

        //replace the non-sorted output
        input.delete();
        if (isCompressed)
        {
            SequenceUtil.bgzip(sorted, input);
        }
        else
        {
            FileUtils.moveFile(sorted, input);
        }
        sorted.delete();
    }
}
