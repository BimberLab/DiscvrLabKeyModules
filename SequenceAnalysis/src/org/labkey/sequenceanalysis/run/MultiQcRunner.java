package org.labkey.sequenceanalysis.run;


import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MultiQcRunner extends AbstractCommandWrapper
{
    public MultiQcRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public File runForFastqc(List<File> inputFastqcs, List<String> extraParams) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();

        args.add(getMultiQc());
        args.add("-o");
        args.add(getOutputDir(inputFastqcs.get(0)).getPath());
        args.add("-z");
        inputFastqcs.forEach(x -> args.add(x.getPath()));

        if (extraParams != null)
        {
            args.addAll(extraParams);
        }

        execute(args);

        File report = new File(getOutputDir(inputFastqcs.get(0)), "multiqc_report.html");
        if (!report.exists())
        {
            throw new PipelineJobException("report not found: " + report.getPath());
        }

        return report;
    }

    private String getMultiQc()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("MULTIQCPATH");
        if (path != null)
        {
            return new File(path, "multiqc").getPath();
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        File baseDir = path == null ? null : new File(path);
        return new File(baseDir, "multiqc").getPath();
    }
}
