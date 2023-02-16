package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.util.FastqQualityFormat;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.util.FastqUtils;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 10/24/12
 * Time: 9:14 AM
 */
public class FastqToSamWrapper extends PicardWrapper
{
    private final FastqQualityFormat _fastqEncoding = null;

    public FastqToSamWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File execute(File file, @Nullable File file2) throws PipelineJobException
    {
        return execute(file, file2, null, null);
    }

    public File execute(File file, @Nullable File file2, @Nullable SAMFileHeader.SortOrder sortOrder, @Nullable SAMReadGroupRecord rg) throws PipelineJobException
    {
        getLogger().info("Converting FASTQ to BAM: " + file.getPath());
        getLogger().info("\tFastqToSam version: " + getVersion());

        execute(getParams(file, file2, sortOrder, rg));
        File output = new File(getOutputDir(file), getOutputFilename(file));
        if (!output.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + output.getPath());
        }

        return output;
    }

    @Override
    protected String getToolName()
    {
        return "FastqToSam";
    }

    private List<String> getParams(File file, File file2, SAMFileHeader.SortOrder sortOrder, @Nullable SAMReadGroupRecord rg) throws PipelineJobException
    {
        List<String> params = getBaseArgs();

        params.add("--FASTQ");
        params.add(file.getPath());

        if (file2 != null)
        {
            params.add("--FASTQ2");
            params.add(file2.getPath());
        }

        if (sortOrder != null)
        {
            params.add("--SORT_ORDER");
            params.add(sortOrder.name());
        }

        if (rg != null)
        {
            params.add("--READ_GROUP_NAME");
            params.add(rg.getReadGroupId());

            if (rg.getPlatform() != null)
            {
                params.add("--PLATFORM");
                params.add(rg.getPlatform());
            }

            if (rg.getPlatformUnit() != null)
            {
                params.add("--PLATFORM_UNIT");
                params.add(rg.getPlatformUnit());
            }

            params.add("--SAMPLE_NAME");
            params.add((rg.getSample() == null ? "SAMPLE" : rg.getSample()));
        }
        else
        {
            params.add("--READ_GROUP_NAME");
            params.add("null");

            params.add("--SAMPLE_NAME");
            params.add("SAMPLE");
        }

        FastqQualityFormat encoding = _fastqEncoding;
        if (encoding == null)
        {
            encoding = FastqUtils.inferFastqEncoding(file);
            if (encoding != null)
            {
                getLogger().info("\tInferred FASTQ encoding of file " + file.getName() + " was: " + encoding.name());
            }
            else
            {
                encoding = FastqQualityFormat.Standard;
                getLogger().warn("\tUnable to infer FASTQ encoding for file: " + file.getPath() + ", defaulting to " + encoding.name());
            }
        }

        params.add("--QUALITY_FORMAT");
        params.add(encoding.name());

        params.add("--OUTPUT");
        params.add(new File(getOutputDir(file), getOutputFilename(file)).getPath());

        return params;
    }

    public String getOutputFilename(File file)
    {
        return FileUtil.getBaseName(file) + ".bam";
    }
}
