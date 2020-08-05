package org.labkey.api.sequenceanalysis.run;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class VariantFiltrationWrapper extends AbstractGatk4Wrapper
{
    public VariantFiltrationWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, File inputVcf, File outputVcf, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running GATK VariantFiltration");

        ensureDictionary(referenceFasta);

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("VariantFiltration");
        args.add("-R");
        args.add(referenceFasta.getPath());

        args.add("-V");
        args.add(inputVcf.getPath());

        args.add("-O");
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
