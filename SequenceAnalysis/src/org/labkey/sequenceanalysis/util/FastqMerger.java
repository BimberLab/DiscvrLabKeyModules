package org.labkey.sequenceanalysis.util;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.PrintWriters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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

    public void mergeFiles(File output, List<File> inputs) throws PipelineJobException
    {
        _logger.info("merging FASTQ files");

        FileType gz = new FileType(".gz");
        try (PrintWriter writer = PrintWriters.getPrintWriter(gz.isType(output) ? new GZIPOutputStream(new FileOutputStream(output, true)) : new FileOutputStream(output, true)))
        {
            for (File f : inputs)
            {
                _logger.info("reading file: " + f.getPath());
                try (BufferedReader reader = Readers.getReader(gz.isType(f) ? new GZIPInputStream(new FileInputStream(f)) : new FileInputStream(f)))
                {
                    IOUtils.copyLarge(reader, writer);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
