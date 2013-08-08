package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/16/12
 * Time: 1:01 PM
 */
public class BowtieRunner extends AbstractCommandWrapper
{
    public BowtieRunner(Logger logger)
    {
        super(logger);
    }

    protected File getExe()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("BOWTIEPATH");
        return new File(path, "bowtie");
    }

    protected File getBuildExe()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("BOWTIEPATH");
        return new File(path, "bowtie-build");
    }

    public void buildIndex(File fasta) throws PipelineJobException
    {
        _logger.info("Creating Bowtie index");

        List<String> args = new ArrayList<>();
        args.add(getBuildExe().getPath());
        args.add("-f");
        args.add("-q");

        args.add(fasta.getPath());
        String outPrefix = FileUtil.getBaseName(fasta) + ".bowtie.index";
        args.add(new File(fasta.getParentFile(), outPrefix).getPath());

        doExecute(getWorkingDir(fasta), args);
    }
}
