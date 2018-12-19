package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 4/4/2017.
 */
public class CombineVariantsWrapper extends AbstractGatkWrapper
{
    public CombineVariantsWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, List<File> inputVcfs, File outputVcf, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running GATK CombineVariants");

        ensureDictionary(referenceFasta);
        try
        {
            for (File inputVcf : inputVcfs)
            {
                SequenceAnalysisService.get().ensureVcfIndex(inputVcf, getLogger());
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("-T");
        args.add("CombineVariants");
        args.add("-R");
        args.add(referenceFasta.getPath());

        for (File f : inputVcfs)
        {
            args.add("-V");
            args.add(f.getPath());
        }

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
