package org.labkey.sequenceanalysis.run.util;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

    public File execute(List<File> inputBams, List<String> sampleIds, @Nullable List<String> notes, File referenceFasta, File outputDir, String name, @Nullable List<String> extraParams) throws PipelineJobException
    {
        getLogger().info("Running RNA-SeQC on BAM: ");
        for (File f : inputBams)
        {
            getLogger().debug(f.getPath());
        }

        List<String> params = new ArrayList<>();

        params.add(SequencePipelineService.get().getJavaFilepath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getJar().getPath());

        if (extraParams != null)
        {
            params.addAll(extraParams);
        }

        //write sample file
        File sampleFile = new File(outputDir, "samples.txt");
        try (CSVWriter writer = new CSVWriter(new FileWriter(sampleFile), '\t'))
        {
            int i = 0;
            for (File f : inputBams)
            {
                String note = notes == null || notes.size() <= i || notes.get(i) == null ? "" : notes.get(i).replaceAll("\\|", ";");
                String sampleId = sampleIds == null || sampleIds.size() <= i || sampleIds.get(i) == null ? null : StringUtils.trimToNull(sampleIds.get(i));
                if (sampleId == null)
                {
                    throw new PipelineJobException("No sampleID provided for file: " + f.getPath());
                }

                writer.writeNext(new String[]{sampleId + "|" + f.getPath() + "|" + note});
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

        File output = new File(outputDir, name);
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

        return output;
    }

    private File getJar()
    {
        return SequencePipelineService.get().getExeForPackage("RNASEQCPATH", "RNA-SeQC.jar");
    }
}
