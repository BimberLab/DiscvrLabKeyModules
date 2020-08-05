package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 6/19/2017.
 */
public class ImmunoGenotypingWrapper extends AbstractDiscvrSeqWrapper
{
    public ImmunoGenotypingWrapper(Logger log)
    {
        super(log);
    }

    public File execute(File inputBam, File referenceFasta, File outputPrefix, List<String> options) throws PipelineJobException
    {
        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("ImmunoGenotyper");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-O");
        args.add(outputPrefix.getPath());
        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);

        File outputTxt = new File(outputPrefix.getPath() + ".genotypes.txt");
        if (!outputTxt.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputTxt.getPath());
        }

        return outputTxt;
    }
}
