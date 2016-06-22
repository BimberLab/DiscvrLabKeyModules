/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.util.FastqQualityFormat;
import htsjdk.samtools.util.QualityEncodingDetector;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * User: bbimber
 * Date: 12/24/11
 * Time: 8:18 AM
 */
public class FastqUtils
{
    public static FileType FqFileType = new FastqFileType();

    public static FastqQualityFormat inferFastqEncoding(File fastq)
    {
        try (FastqReader reader = new FastqReader(fastq))
        {
            QualityEncodingDetector detector = new QualityEncodingDetector();
            detector.add(QualityEncodingDetector.DEFAULT_MAX_RECORDS_TO_ITERATE * 2, reader);

            //NOTE: even though this is a FASTQ file, use SAM since this will default to standard encoding (the current norm), instead of FASTQ which defaults to illumina.
            //this is only relevant for situations of ambiguous encoding.
            return detector.generateBestGuess(QualityEncodingDetector.FileContext.SAM, null);
        }
    }

    public static long getSequenceCount(File inputFile) throws PipelineJobException
    {
        FileType gz = new FileType(".gz");
        try (InputStream is = gz.isType(inputFile) ? new GZIPInputStream(new FileInputStream(inputFile)) : new FileInputStream(inputFile);BufferedReader lnr = new BufferedReader(new InputStreamReader(is));)
        {
            int count = 0;
            while (lnr.readLine() != null)
            {
                count++;
            }

            return count / 4L;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e.getMessage());
        }
    }

    public static Pair<Long, Long> logSequenceCounts(File inputFile1, File inputFile2, Logger log, @Nullable Long previousCount1, @Nullable Long previousCount2) throws PipelineJobException
    {
        final long bytes = 10737418240L; //10gb

        Long count1 = null;
        long size1 = FileUtils.sizeOf(inputFile1);
        if (size1 > bytes)
        {
            log.info("\t" + inputFile1.getName() + ": " + FileUtils.byteCountToDisplaySize(size1));
        }
        else
        {
            count1 = FastqUtils.getSequenceCount(inputFile1);
            log.info("\t" + inputFile1.getName() + ": " + count1 + " sequences" + (previousCount1 != null ? ", difference from initial: " + (previousCount1 - count1) : ""));
        }

        Long count2 = null;
        if (inputFile2 != null)
        {
            long size2 = FileUtils.sizeOf(inputFile2);
            if (size2 > bytes)
            {
                log.info("\t" + inputFile2.getName() + ": " + FileUtils.byteCountToDisplaySize(size2));
            }
            else
            {
                count2 = inputFile2 == null ? null : FastqUtils.getSequenceCount(inputFile2);
                log.info("\t" + inputFile2.getName() + ": " + count2 + " sequences" + (previousCount2 != null ? ", difference from initial: " + (previousCount2 - count2) : ""));
            }
        }

        return Pair.of(count1, count2);
    }
    
    public static Map<String, Object> getQualityMetrics(File f, @Nullable Logger log)
    {
        if (log != null)
        {
            log.info("calculating quality metrics for file: " + f.getName());
        }

        try (FastqReader reader = new FastqReader(f))
        {
            long total = 0;
            long sum = 0;
            long min = 0;
            long max = 0;
            float avg;

            long len;
            while (reader.hasNext())
            {
                FastqRecord fq = reader.next();
                len = fq.getReadString().length();

                total++;
                if (len < min || min == 0)
                    min = len;
                if (len > max)
                    max = len;

                sum += len;

                if (log != null && total % 1000000L == 0)
                {
                    log.info("processed " + NumberFormat.getInstance().format(total) + " reads");
                }
            }

            if (log != null)
            {
                log.info("processed " + NumberFormat.getInstance().format(total) + " reads");
            }

            avg = sum / (float)total;

            Map<String, Object> map = new HashMap<>();
            map.put("Total Reads", total);
            map.put("Min Read Length", min);
            map.put("Max Read Length", max);
            map.put("Mean Read Length", avg);

            return map;
        }
    }

    public static void mergeFastqFiles(File output, File... inputs) throws IOException
    {
        if (!output.exists())
        {
            output.createNewFile();
        }

        FileType gz = new FileType(".gz");
        for (File f : inputs)
        {
            if (!f.exists())
            {
                throw new NotFoundException("File " + f.getPath() + " does not exist");
            }

            try (InputStream in = gz.isType(f) ? new GZIPInputStream(new FileInputStream(f)) : new FileInputStream(f);BufferedWriter writer = new BufferedWriter(new FileWriter(output)))
            {
                IOUtils.copy(in, writer);
            }
        }
    }

    public static Integer getQualityOffset(FastqQualityFormat encoding)
    {
        if (FastqQualityFormat.Illumina == encoding || FastqQualityFormat.Solexa == encoding)
        {
            return 64;
        }
        else if (FastqQualityFormat.Standard == encoding)
        {
            return 33;
        }
        else
        {
            return null;
        }
    }
}