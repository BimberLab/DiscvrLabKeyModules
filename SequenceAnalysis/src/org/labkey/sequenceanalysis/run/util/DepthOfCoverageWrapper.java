package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 4/24/2017.
 */
public class DepthOfCoverageWrapper extends AbstractGatkWrapper
{
    public DepthOfCoverageWrapper(Logger log)
    {
        super(log);
    }

    public void run(List<File> inputBams, String outputBaseName, File referenceFasta, @Nullable List<String> options) throws PipelineJobException
    {
        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("-T");
        args.add("DepthOfCoverage");
        args.add("-R");
        args.add(referenceFasta.getPath());
        for (File f : inputBams)
        {
            args.add("-I");
            args.add(f.getPath());
        }
        args.add("-o");
        args.add(outputBaseName);
        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);
    }
}
