package org.labkey.sequenceanalysis.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.PrintWriters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
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
        _logger.info("merging FASTQ files: " + inputs.size());

        FileType gz = new FileType(".gz");
        if (gz.isType(output) && !SystemUtils.IS_OS_WINDOWS)
        {
            mergeUsingCat(output, inputs);
        }
        else
        {
            try (PrintWriter writer = PrintWriters.getPrintWriter(gz.isType(output) ? new GZIPOutputStream(new FileOutputStream(output)) : new FileOutputStream(output)))
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

    private void mergeUsingCat(File output, List<File> inputs) throws PipelineJobException
    {
        List<String> bashCommands = new ArrayList<>();
        for (File f : inputs)
        {
            String cat = f.getName().toLowerCase().endsWith(".gz") ? "zcat" : "cat";
            bashCommands.add(cat + " " + f.getPath());
        }

        try
        {
            File bashTmp = new File(output.getParentFile(), "fastqMerge.sh");
            try (PrintWriter writer = PrintWriters.getPrintWriter(bashTmp))
            {
                writer.write("#!/bin/bash\n");
                writer.write("set -x\n");
                writer.write("set -e\n");
                writer.write("{\n");
                bashCommands.forEach(x -> writer.write(x + '\n'));

                writer.write("} | gzip -c > " + output + "\n");
            }

            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(_logger);
            wrapper.execute(Arrays.asList("/bin/bash", bashTmp.getPath()));

            bashTmp.delete();
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

    }
}
