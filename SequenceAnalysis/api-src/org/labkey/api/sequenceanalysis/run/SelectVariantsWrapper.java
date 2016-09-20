package org.labkey.api.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class SelectVariantsWrapper extends AbstractGatkWrapper
{
    public SelectVariantsWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, File inputVcf, File outputVcf, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running GATK SelectVariants");

        ensureDictionary(referenceFasta);

        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJavaFilepath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("SelectVariants");
        args.add("-R");
        args.add(referenceFasta.getPath());

        args.add("-V");
        args.add(inputVcf.getPath());

        args.add("-o");
        args.add(outputVcf.getPath());

        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputVcf.getPath());
        }
    }
}
