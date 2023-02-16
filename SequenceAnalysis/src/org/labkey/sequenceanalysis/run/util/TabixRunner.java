package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by bimber on 9/2/2014.
 */
public class TabixRunner extends AbstractCommandWrapper
{
    public TabixRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public File execute(File input) throws PipelineJobException
    {
        getLogger().info("Building tabix index for file: " + input.getPath());

        File output = new File(input.getPath() + ".tbi");
        if (output.exists())
        {
            getLogger().debug("deleting pre-existing index: " + output.getPath());
            output.delete();
        }

        execute(getParams(input));
        if (!output.exists())
            throw new PipelineJobException("Index not created, expected: " + output.getPath());

        getLogger().info("done");

        return output;
    }

    public List<String> getParams(File input)
    {
        List<String> params = new ArrayList<>();
        params.add(getExe().getPath());
        params.add("-f");

        String type = null;
        if (new FileType(Arrays.asList("gff3", "gff", "gtf"), "gff", FileType.gzSupportLevel.SUPPORT_GZ).isType(input))
        {
            type = "gff";
        }
        else if (new FileType(Arrays.asList("bed", "bedGraph"), "bed", FileType.gzSupportLevel.SUPPORT_GZ).isType(input))
        {
            type = "bed";
        }
        else if (new FileType(Arrays.asList("sam", "bam"), "bam").isType(input))
        {
            type = "sam";
        }
        else if (new FileType(List.of("vcf"), "vcf", FileType.gzSupportLevel.SUPPORT_GZ).isType(input))
        {
            type = "vcf";
        }

        if (type != null)
        {
            params.add("-p");
            params.add(type);
        }

        params.add(input.getPath());
        return params;
    }

    public File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("TABIXPATH", "tabix");
    }
}
