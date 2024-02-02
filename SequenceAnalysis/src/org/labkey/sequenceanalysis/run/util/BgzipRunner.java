package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 9/2/2014.
 */
public class BgzipRunner extends AbstractCommandWrapper
{
    private int _maxThreads = -1;
    private Integer _compressionLevel = null;

    public BgzipRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public void setMaxThreads(int maxThreads)
    {
        _maxThreads = maxThreads;
    }

    public void setCompressionLevel(int compressionLevel)
    {
        _compressionLevel = compressionLevel;
    }

    public File execute(File input) throws PipelineJobException
    {
        return execute(input, false);
    }

    public File execute(File input, boolean preserveInput) throws PipelineJobException
    {
        getLogger().info("BGZipping file: " + input.getPath());

        execute(getParams(input, preserveInput));
        File output = new File(input.getPath() + ".gz");
        if (!output.exists())
            throw new PipelineJobException("Output not created, expected: " + output.getPath());

        if (!preserveInput && input.exists())
        {
            getLogger().debug("deleting input: " + input.getPath());
            input.delete();
        }

        return output;
    }

    private List<String> getParams(File input, boolean preserveInput)
    {
        List<String> params = new ArrayList<>();
        params.add(getExe().getPath());
        params.add("-f");

        if (preserveInput)
        {
            params.add("-k");
        }

        Integer threads;
        if (_maxThreads == -1)
        {
            threads = SequencePipelineService.get().getMaxThreads(getLogger());
        }
        else
        {
            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
            threads = maxThreads == null ? _maxThreads : Math.min(_maxThreads, maxThreads);
        }

        if (threads != null)
        {
            params.add("--threads");
            params.add(threads.toString());
        }

        if (_compressionLevel != null)
        {
            params.add("--compress-level");
            params.add(_compressionLevel.toString());
        }

        params.add(input.getPath());

        return params;
    }

    public static File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BGZIPPATH", "bgzip");
    }
}
