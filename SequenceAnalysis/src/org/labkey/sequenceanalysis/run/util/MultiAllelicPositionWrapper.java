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
public class MultiAllelicPositionWrapper extends AbstractGatkWrapper
{
    public MultiAllelicPositionWrapper(Logger log)
    {
        super(log);
    }

    public File run(List<File> inputBams, File outputFile, File referenceFasta, @Nullable List<String> options) throws PipelineJobException
    {
        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("-T");
        args.add("MultipleAllelesAtLoci");
        args.add("-R");
        args.add(referenceFasta.getPath());
        for (File f : inputBams)
        {
            args.add("-I");
            args.add(f.getPath());
        }
        args.add("-o");
        args.add(outputFile.getPath());
        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);

        return outputFile;
    }

    @Override
    protected String getJarName()
    {
        return "GenomeAnalysisTK-discvr.jar";
    }
}
