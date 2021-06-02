package org.labkey.sequenceanalysis.run.variant;

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
public class VariantQCWrapper extends AbstractDiscvrSeqWrapper
{
    public VariantQCWrapper(Logger log)
    {
        super(log);
    }

    @Override
    protected String getJarName()
    {
        return "DISCVRSeq.jar";
    }

    public File execute(File inputVCF, File referenceFasta, File outputHtml, List<String> options) throws PipelineJobException
    {
        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("VariantQC");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-V");
        args.add(inputVCF.getPath());
        args.add("-O");
        args.add(outputHtml.getPath());
        args.add("--ignore-variants-starting-outside-interval");
        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);
        if (!outputHtml.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputHtml.getPath());
        }

        return outputHtml;
    }
}
