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

import net.sf.picard.fastq.FastqReader;
import net.sf.picard.fastq.FastqRecord;
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
        FastqReader reader = null;
        try
        {
            reader = new FastqReader(fastq);
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
        finally
        {
            if (reader != null)
                reader.close();
        }

        return null;
    }

    public static int getSequenceCount(File inputFile) throws PipelineJobException
    {
        BufferedReader lnr = null;
        InputStream is = null;
        FileType gz = new FileType(".gz");

        try
        {
            is = new FileInputStream(inputFile);
            if (gz.isType(inputFile))
            {
                is = new GZIPInputStream(is);
            }

            lnr = new BufferedReader(new InputStreamReader(is));
            int count = 0;
            while (lnr.readLine() != null)
            {
                count++;
            }
            return count / 4;
        }
        catch (FileNotFoundException e)
        {
            throw new PipelineJobException(e.getMessage());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e.getMessage());
        }
        finally
        {
            try
            {
                if (lnr != null)
                    lnr.close();
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e.getMessage());
            }
        }
    }

    public static Map<String, Object> getQualityMetrics(File f)
    {
        FastqReader reader = null;
        try
        {
            int total = 0;
            int sum = 0;
            int min = 0;
            int max = 0;
            float avg;

            reader = new FastqReader(f);
            int l;
            while(reader.hasNext())
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
        finally
        {
            if (reader != null)
                reader.close();
        }
    }

    public static void mergeFastqFiles(File output, File... inputs) throws IOException
    {
        if (!output.exists())
        {
            output.createNewFile();
        }

        FileInputStream in = null;
        GZIPInputStream gis = null;
        FileType gz = new FileType(".gz");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output)))
        {
            for (File f : inputs)
            {
                if (!f.exists())
                {
                    throw new NotFoundException("File " + f.getPath() + " does not exist");
                }
                in = new FileInputStream(f);

                if (gz.isType(f))
                {
                    gis = new GZIPInputStream(in);
                    IOUtils.copy(gis, writer);
                }
                else
                {
                    IOUtils.copy(in, writer);
                }
            }
        }
        finally
        {
            if (in != null)
                in.close();
            if (gis != null)
                gis.close();
        }
    }
}