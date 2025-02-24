package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 12/15/12
 * Time: 9:11 PM
 */
public class BcftoolsRunner extends AbstractCommandWrapper
{
    public BcftoolsRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public static File getBcfToolsPath()
    {
        return SequencePipelineService.get().getExeForPackage("BCFTOOLSPATH", "bcftools");
    }

    public static boolean isBcftoolsFound()
    {
        return BcftoolsRunner.resolveFileInPath("bcftools", null, false) != null;
    }

    public void doIndex(File vcf) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getBcfToolsPath().getAbsolutePath());
        args.add("index");
        args.add("-t");
        args.add("-f");
        args.add("-n");

        if (!PipelineJobService.get().isWebServer())
        {
            Integer threads = SequencePipelineService.get().getMaxThreads(getLogger());
            if (threads != null)
            {
                args.add("--threads");
                args.add(String.valueOf(threads));
            }
        }

        args.add(vcf.getAbsolutePath());

        execute(args);
    }
}
