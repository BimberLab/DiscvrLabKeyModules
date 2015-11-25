package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class SplitNCigarReadsWrapper extends AbstractGatkWrapper
{
    public SplitNCigarReadsWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, File inputBam, File outputBam) throws PipelineJobException
    {
        getLogger().info("Running GATK SplitNCigarReads");

        ensureDictionary(referenceFasta);

        List<String> args = new ArrayList<>();
        args.add("java");
        args.addAll(getBaseParams());
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("SplitNCigarReads");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-o");
        args.add(outputBam.getPath());
        args.add("-U");
        args.add("ALLOW_N_CIGARS");
        args.add("-rf");
        args.add("ReassignMappingQuality");
        args.add("-DMQ");
        args.add("60");

        execute(args);

        if (!outputBam.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputBam.getPath());
        }
    }
}
