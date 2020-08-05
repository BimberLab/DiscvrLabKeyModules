package org.labkey.sequenceanalysis.run;


import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MultiQcRunner extends AbstractCommandWrapper
{
    public MultiQcRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public File runForFiles(Collection<File> filePaths, File outDir, List<String> extraParams) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();

        args.add(getMultiQc());
        args.add("-o");
        args.add(outDir.getPath());
        args.add("-z");
        args.add("--ignore");
        args.add("Undetermined*");
        args.add("--ignore");
        args.add("undetermined*");
        args.add("--ignore");
        args.add("*._STARpass1");

        args.add("-e");
        args.add("bcl2fastq");

        if (extraParams != null)
        {
            args.addAll(extraParams);
        }

        filePaths.forEach(x -> args.add(x.getPath()));

        execute(args);

        File report = new File(outDir, "multiqc_report.html");
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
