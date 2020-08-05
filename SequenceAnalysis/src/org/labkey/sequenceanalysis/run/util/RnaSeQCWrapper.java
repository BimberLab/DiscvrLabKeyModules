package org.labkey.sequenceanalysis.run.util;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.run.alignment.BWAWrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 12/15/12
 * Time: 9:07 PM
 */
public class RnaSeQCWrapper extends AbstractCommandWrapper
{
    public RnaSeQCWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File execute(List<File> inputBams, List<String> sampleIds, @Nullable List<String> notes, File referenceFasta, File gtfFile, File outputDir, String name, @Nullable List<String> extraParams, @Nullable File rnaFasta) throws PipelineJobException
    {
        getLogger().info("Running RNA-SeQC on BAM: ");
        for (File f : inputBams)
        {
            getLogger().debug(f.getPath());
        }

        List<String> params = new ArrayList<>();

        params.add(getJava7Filepath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getJar().getPath());

        if (extraParams != null)
        {
            params.addAll(extraParams);
        }

        File localRnaFasta = null;
        if (rnaFasta != null)
        {
            localRnaFasta = prepareBwaIndex(rnaFasta, outputDir);

            params.add("-BWArRNA");
            params.add(localRnaFasta.getPath());

            File bwaExe = new BWAWrapper(getLogger()).getExe();
            if (bwaExe != null)
            {
                params.add("-bwa");
                params.add(bwaExe.getPath());
            }
        }

        //write sample file
        File sampleFile = new File(outputDir, "samples.txt");
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(sampleFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            writer.writeNext(new String[]{"Sample ID", "Bam File", "Notes"});

            int i = 0;
            for (File f : inputBams)
            {
                String note = notes == null || notes.size() <= i || notes.get(i) == null ? "N/A" : notes.get(i);
                String sampleId = sampleIds == null || sampleIds.size() <= i || sampleIds.get(i) == null ? null : StringUtils.trimToNull(sampleIds.get(i));
                if (sampleId == null)
                {
                    throw new PipelineJobException("No sampleID provided for file: " + f.getPath());
                }

                writer.writeNext(new String[]{sampleId, f.getPath(), note});
                i++;
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        params.add("-s");
        params.add(sampleFile.getPath());

        params.add("-r");
        params.add(referenceFasta.getPath());

        //simplistic filtering of GTF for required fields
        File filteredGtf = new File(outputDir, "filtered.gtf");
        try (BufferedReader reader = Readers.getReader(gtfFile); PrintWriter writer = PrintWriters.getPrintWriter(filteredGtf))
        {
            getLogger().info("filtering GTF for required fields");
            getLogger().info("original GTF: " + gtfFile.getPath());
            String line;
            int lineNo = 0;
            int totalLines = 0;
            int filteredLines = 0;
            while ((line = reader.readLine()) != null)
            {
                lineNo++;

                if (line.startsWith("#"))
                {
                    writer.write(line);
                    writer.write('\n');
                    totalLines++;
                }
                else if (!line.contains("transcript_id"))
                {
                    getLogger().info("skipping GTF line " + lineNo + " because it lacks transcript_id");
                    filteredLines++;
                }
                else if (!line.contains("gene_id"))
                {
                    getLogger().info("skipping GTF line " + lineNo + " because it lacks gene_id");
                    filteredLines++;
                }
                else
                {
                    writer.write(line);
                    writer.write('\n');
                    totalLines++;
                }
            }

            getLogger().info(String.format("total lines in original GTF: %d, in filtered GTF: %d, total filtered: %d", lineNo, totalLines, filteredLines));
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        params.add("-t");
        params.add(filteredGtf.getPath());

        String fn = FileUtil.makeLegalName(name).replaceAll(" ", "_");
        File output = new File(outputDir, fn);
        params.add("-o");
        params.add(output.getPath());

        execute(params);

        if (!output.exists())
            throw new PipelineJobException("Output not created, expected: " + output.getPath());

        File index = new File(output, "index.html");
        if (!index.exists())
        {
            throw new PipelineJobException("Expected index.html file does not exist: " + index.getPath());
        }

        if (filteredGtf.exists())
        {
            filteredGtf.delete();
        }

        if (localRnaFasta != null)
        {
            try
            {
                getLogger().info("deleting local copy of rRNA FASTA: " + localRnaFasta.getParentFile().getPath());
                FileUtils.deleteDirectory(localRnaFasta.getParentFile());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return output;
    }

    private File getJar()
    {
        return SequencePipelineService.get().getExeForPackage("RNASEQCPATH", "RNA-SeQC.jar");
    }

    private String getJava7Filepath()
    {
        String javaDir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_HOME_7");
        if (javaDir != null)
        {
            File ret = new File(javaDir, "bin");
            ret = new File(ret, "java");
            return ret.getPath();
        }

        //if not explicitly found, try the default
        javaDir = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_HOME");
        if (javaDir != null)
        {
            File ret = new File(javaDir, "bin");
            ret = new File(ret, "java");
            return ret.getPath();
        }

        return "java";
    }

    private File prepareBwaIndex(File fasta, File outdir) throws PipelineJobException
    {
        try
        {
            getLogger().info("Copying/preparing rRNA FASTA");
            File subdir = new File(outdir, "bwaIndex");
            if (!subdir.exists())
            {
                subdir.mkdirs();
            }

            File copy = new File(subdir, fasta.getName());
            if (copy.exists())
            {
                copy.delete();
            }

            FileUtils.copyFile(fasta, copy);

            BWAWrapper wrapper = new BWAWrapper(getLogger());

            List<String> args = new ArrayList<>();
            args.add(wrapper.getExe().getPath());
            args.add("index");

            args.add(copy.getPath());
            wrapper.execute(args);

            return copy;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
