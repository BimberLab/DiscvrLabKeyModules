package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 4/5/2017.
 */
public class GFFReadWrapper extends AbstractCommandWrapper
{
    public GFFReadWrapper(Logger log)
    {
        super(log);
    }

    public File sortGxf(File gxf, @Nullable File output) throws PipelineJobException
    {
        getLogger().info("Running gffread on file: " + gxf.getPath());
        try
        {
            File outputFile;
            if (output == null)
            {
                getLogger().debug("file will sort sorted in place");
                outputFile = File.createTempFile("gffread", ".txt");
            }
            else
            {
                outputFile = output;
            }

            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());
            args.add("-F");
            args.add("-O");
            args.add("-E");

            if (SequenceUtil.FILETYPE.gtf.getFileType().isType(gxf))
            {
                args.add("-T");
            }

            args.add("-o");
            args.add(outputFile.getPath());

            args.add(gxf.getPath());

            execute(args);

            if (!outputFile.exists())
            {
                throw new PipelineJobException("Unable to find expected output: " + outputFile.getPath());
            }

            //do one more sort, to make sure we end with correct chromosome order
            SequenceUtil.sortROD(outputFile, getLogger(), null);

            if (output == null)
            {
                getLogger().debug("replacing input with sorted file:");
                gxf.delete();
                FileUtils.moveFile(outputFile, gxf);
                outputFile = gxf;
            }

            return outputFile;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("GFFREADATH", "gffread");
    }
}
