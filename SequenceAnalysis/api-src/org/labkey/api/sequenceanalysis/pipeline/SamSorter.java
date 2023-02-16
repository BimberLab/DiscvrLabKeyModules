package org.labkey.api.sequenceanalysis.pipeline;

import htsjdk.samtools.SAMFileHeader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 12/15/12
 * Time: 9:07 PM
 */
public class SamSorter extends SamtoolsRunner
{
    private static final String COMMAND = "sort";

    public SamSorter(@Nullable Logger logger)
    {
        super(logger);
    }

    public File execute(File input, @Nullable File output, SAMFileHeader.SortOrder sortOrder) throws PipelineJobException
    {
        return execute(input, output, sortOrder, null);
    }

    public File execute(File input, @Nullable File output, SAMFileHeader.SortOrder sortOrder, @Nullable List<String> extraArgs) throws PipelineJobException
    {
        getLogger().info("Sorting SAM/BAM: " + input.getPath());

        boolean replaceOriginal = output == null;
        if (output == null)
        {
            output = new File(getOutputDir(input), FileUtil.getBaseName(input) + ".sorted" + "." + FileUtil.getExtension(input));
        }

        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add(COMMAND);

        getLogger().info("order: " + sortOrder.name());
        if (sortOrder == SAMFileHeader.SortOrder.queryname)
        {
            params.add("-n");
        }
        else if (sortOrder != SAMFileHeader.SortOrder.coordinate)
        {
            getLogger().debug("Using sort order: " + sortOrder.name());
        }

        Integer threads = SequencePipelineService.get().getMaxThreads(getLogger());
        if (threads != null)
        {
            params.add("-@");
            params.add(threads.toString());
        }

        params.add("-l");
        params.add("9");

        String tmpDir = StringUtils.trimToNull(PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("JAVA_TMP_DIR"));
        if (tmpDir == null)
        {
            tmpDir = System.getProperty("java.io.tmpdir");
        }
        params.add("-T");
        params.add(tmpDir);

        if (extraArgs != null)
        {
            params.addAll(extraArgs);
        }

        params.add("-o");
        params.add(output.getPath());

        params.add(input.getPath());

        execute(params);

        if (!output.exists())
            throw new PipelineJobException("Output BAM not created, expected: " + output.getPath());

        if (replaceOriginal)
        {
            try
            {
                getLogger().info("\treplacing original file with sorted BAM");

                input.delete();

                //note: if there is a pre-existing index, we need to delete this since it is out of date
                File idx = SequencePipelineService.get().getExpectedIndex(output);
                if (idx.exists())
                {
                    getLogger().debug("deleting old BAM index");
                    idx.delete();
                }

                FileUtils.moveFile(output, input);

                output = input;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        if (sortOrder == SAMFileHeader.SortOrder.coordinate)
        {
            SequencePipelineService.get().ensureBamIndex(output, getLogger(), true);
        }

        return output;
    }
}
