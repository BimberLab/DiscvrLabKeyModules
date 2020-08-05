package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

public class GxfSorter
{
    Logger _log;

    public GxfSorter(Logger log)
    {
        _log = log;
    }

    public File sortGxf(File input, @Nullable File output) throws PipelineJobException
    {
        if (SequenceUtil.FILETYPE.gff.getFileType().isType(input))
        {
            return sortGff(input, output);
        }
        else if (SequenceUtil.FILETYPE.gtf.getFileType().isType(input))
        {
            return sortGtf(input, output);
        }
        else
        {
            throw new IllegalArgumentException("Not a GTF/GFF file: " + input.getName());
        }
    }

    public File sortGff(File input, @Nullable File output) throws PipelineJobException
    {
        File baseDir = output == null ? input.getParentFile() : output.getParentFile();
        File outputFile = output == null ? new File(input.getParentFile(), "temp.sorted.gff") : output;

        File script = new File(baseDir, "sorter.sh");
        try (PrintWriter writer = PrintWriters.getPrintWriter(script))
        {
            writer.println("#!/bin/bash");
            writer.println("set -x");
            writer.println("set -e");
            writer.println("GFF=" + input.getPath());
            writer.println("OUT_GFF=" + outputFile.getPath());

            writer.println("awk '{ if ($1 ~ \"^#\" ) print $0; else exit; }' $GFF > $OUT_GFF");
            writer.println("(grep -v '#' $GFF | grep -v \"Parent=\" | sort -V -k1,1 -k4,4n -k5,5n; grep -v '#' $GFF | grep -e \"Parent=\" | sort -V -k1,1 -k4,4n -k5,5n)| sort -V -k1,1 -k4,4n -s >> $OUT_GFF");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return executeScript(script, input, output, outputFile);
    }

    public File sortGtf(File input, @Nullable File output) throws PipelineJobException
    {
        File baseDir = output == null ? input.getParentFile() : output.getParentFile();
        File outputFile = output == null ? new File(input.getParentFile(), "temp.sorted.gtf") : output;

        File script = new File(baseDir, "sorter.sh");
        try (PrintWriter writer = PrintWriters.getPrintWriter(script))
        {
            writer.println("#!/bin/bash");
            writer.println("set -x");
            writer.println("set -e");
            writer.println("GTF=" + input.getPath());
            writer.println("OUT_GTF=" + outputFile.getPath());

            writer.println("awk '{ if ($1 ~ \"^#\" ) print $0; else exit; }' $GTF > $OUT_GTF");
            writer.println("cat $GTF | grep -v '#' | awk -v OFS='\\t' ' {");
            writer.println("so = 3");
            writer.println("if (tolower($3) == \"gene\")");
            writer.println("    so = 1");
            writer.println("else if (tolower($3) == \"transcript\" || tolower($3) == \"mrna\")");
            writer.println("    so = 2");
            writer.println("else if (tolower($3) == \"exon\")");
            writer.println("    so = 3");
            writer.println("else if (tolower($3) == \"cds\")");
            writer.println("    so = 4");
            writer.println("print so, $0 } ' | sort -V -k2,2 -k5,5n -k1,1n | cut -d$'\\t' -f2- >> $OUT_GTF");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return executeScript(script, input, output, outputFile);
    }

    private File executeScript(File script, File input, File output, File outputFile) throws PipelineJobException
    {
        SimpleScriptWrapper wrapper = new SimpleScriptWrapper(_log);
        wrapper.setWorkingDir(outputFile.getParentFile());
        wrapper.execute(Arrays.asList("/bin/bash", script.getPath()));

        long countOrig = SequenceUtil.getLineCount(input);
        long countSort = SequenceUtil.getLineCount(outputFile);
        if (countOrig != countSort)
        {
            _log.warn("Input and sorted do not have the same line count: " + countOrig + " / " + countSort);
        }

        if (output == null)
        {
            _log.info("Replacing input with sorted: " + input.getPath());
            try
            {
                input.delete();
                FileUtils.moveFile(outputFile, input);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return output == null ? input : output;
    }
}
