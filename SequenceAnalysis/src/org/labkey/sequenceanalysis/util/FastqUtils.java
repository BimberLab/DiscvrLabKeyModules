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
import org.apache.commons.io.IOUtils;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileType;
import org.labkey.api.view.NotFoundException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
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

    public static enum FASTQ_ENCODING {
        Illumina(64), //ascii64
        Standard(33); //ascii33

        FASTQ_ENCODING(int offset)
        {
            this.offset = offset;
        }

        public int offset;

    }

    public static FastqUtils.FASTQ_ENCODING inferFastqEncoding(File fastq)
    {
        try (FastqReader reader = new FastqReader(fastq))
        {
            Iterator<FastqRecord> i = reader.iterator();
            while (i.hasNext())
            {
                FastqRecord fq = i.next();
                String quals = fq.getBaseQualityString();
                for (char c : quals.toCharArray())
                {
                    byte val = (byte)c;
                    if (val > 73)
                    {
                        return FASTQ_ENCODING.Illumina;
                    }
                    else if (val < 59)
                    {
                        return FASTQ_ENCODING.Standard;
                    }
                }

            }
        }

        return null;
    }

    public static int getSequenceCount(File inputFile) throws PipelineJobException
    {
        FileType gz = new FileType(".gz");
        try (InputStream is = gz.isType(inputFile) ? new GZIPInputStream(new FileInputStream(inputFile)) : new FileInputStream(inputFile);BufferedReader lnr = new BufferedReader(new InputStreamReader(is));)
        {
            int count = 0;
            while (lnr.readLine() != null)
            {
                count++;
            }

            return count / 4;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e.getMessage());
        }
    }

    public static Map<String, Object> getQualityMetrics(File f)
    {
        try (FastqReader reader = new FastqReader(f))
        {
            int total = 0;
            int sum = 0;
            int min = 0;
            int max = 0;
            float avg;

            int l;
            while (reader.hasNext())
            {
                FastqRecord fq = reader.next();
                l = fq.getReadString().length();

                total++;
                if(l < min || min == 0)
                    min = l;
                if(l > max)
                    max = l;

                sum += l;
            }

            avg = sum / total;

            Map<String, Object> map = new HashMap<>();
            map.put("Total Sequences", total);
            map.put("Min Sequence Length", min);
            map.put("Max Sequence Length", max);
            map.put("Avg Sequence Length", avg);

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
}