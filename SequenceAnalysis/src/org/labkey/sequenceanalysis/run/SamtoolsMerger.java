package org.labkey.sequenceanalysis.run;

import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SamtoolsMerger extends SamtoolsRunner
{
    private static final String COMMAND = "merge";

    public SamtoolsMerger(Logger log)
    {
        super(log);
    }

    public File mergeBams(List<File> inputBams, File outputFile) throws PipelineJobException
    {
        getLogger().info("Merging SAM/BAM(s):");

        List<String> params = new ArrayList<>();
        params.add(getSamtoolsPath().getPath());
        params.add(COMMAND);
        params.add("-o");
        params.add(outputFile.getPath());

        Integer threads = SequencePipelineService.get().getMaxThreads(getLogger());
        if (threads != null)
        {
            params.add(" --threads");
            params.add(String.valueOf(threads));
        }

        inputBams.forEach(f -> params.add(f.getPath()));

        execute(params);

        File idx = SequencePipelineService.get().ensureBamIndex(outputFile, getLogger(), false);
        if (!idx.exists())
        {
            throw new PipelineJobException("Unable to find BAM index: " + idx.getPath());
        }

        return outputFile;
    }
}
