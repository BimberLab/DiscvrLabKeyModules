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
    private FastqQualityFormat _fastqEncoding = null;

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

    protected String getToolName()
    {
        return "FastqToSam";
    }

    private List<String> getParams(File file, File file2, SAMFileHeader.SortOrder sortOrder, @Nullable SAMReadGroupRecord rg) throws PipelineJobException
    {
        List<String> params = getBaseArgs();
        inferMaxRecordsInRam(params);
        params.add("FASTQ=" + file.getPath());
        if (file2 != null)
            params.add("FASTQ2=" + file2.getPath());

        if (sortOrder != null)
        {
            params.add("SORT_ORDER=" + sortOrder.name());
        }

        if (rg != null)
        {
            params.add("READ_GROUP_NAME=" + rg.getReadGroupId());

            if (rg.getPlatform() != null)
                params.add("PLATFORM=" + rg.getPlatform());

            if (rg.getPlatformUnit() != null)
                params.add("PLATFORM_UNIT=" + rg.getPlatformUnit());

            if (rg.getPlatformUnit() != null)
                params.add("SAMPLE_NAME=" + rg.getSample());
        }
        else
        {
            params.add("READ_GROUP_NAME=null");
            params.add("SAMPLE_NAME=SAMPLE");
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

        params.add("QUALITY_FORMAT=" + encoding);
        params.add("OUTPUT=" + new File(getOutputDir(file), getOutputFilename(file)).getPath());

        return params;
    }

    public String getOutputFilename(File file)
    {
        return FileUtil.getBaseName(file) + ".bam";
    }
}
