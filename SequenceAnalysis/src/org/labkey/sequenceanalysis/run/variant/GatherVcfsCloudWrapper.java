package org.labkey.sequenceanalysis.run.variant;

import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class GatherVcfsCloudWrapper extends AbstractGatk4Wrapper
{
    public GatherVcfsCloudWrapper(Logger log)
    {
        super(log);
    }

    public void gatherVcfs(File output, List<File> inputVcfs) throws PipelineJobException
    {
        List<String> args = new ArrayList<>(getBaseArgs("GatherVcfsCloud"));
        args.add("-O");
        args.add(output.getPath());

        File argFile = new File(output.getParentFile(), "inputs.list");
        try (PrintWriter writer = PrintWriters.getPrintWriter(argFile))
        {
            inputVcfs.forEach(f -> writer.println(f.getPath()));
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        args.add("-I");
        args.add(argFile.getPath());

        execute(args);

        argFile.delete();

        try
        {
            SequenceAnalysisService.get().ensureVcfIndex(output, getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
