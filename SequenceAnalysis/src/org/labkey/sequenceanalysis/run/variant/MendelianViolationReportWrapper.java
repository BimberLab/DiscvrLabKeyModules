package org.labkey.sequenceanalysis.run.variant;

import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 6/19/2017.
 */
public class MendelianViolationReportWrapper extends AbstractDiscvrSeqWrapper
{
    public MendelianViolationReportWrapper(Logger log)
    {
        super(log);
    }

    public File execute(File inputVCF, File referenceFasta, File outputTxt, File ped, List<String> options) throws PipelineJobException
    {
        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("MendelianViolationReport");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-V");
        args.add(inputVCF.getPath());
        args.add("-ped");
        args.add(ped.getPath());
        args.add("-pedValidationType");
        args.add("SILENT");
        args.add("-O");
        args.add(outputTxt.getPath());
        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);
        if (!outputTxt.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputTxt.getPath());
        }

        return outputTxt;
    }
}
