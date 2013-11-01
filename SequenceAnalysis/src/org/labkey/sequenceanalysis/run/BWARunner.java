package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.pipeline.SequencePipelineSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/16/12
 * Time: 11:16 AM
 */
public class BWARunner extends AbstractAlignerWrapper
{
    public BWARunner(Logger logger)
    {
        super(logger);
    }

    public String getName()
    {
        return "BWA";
    }

    public String getDescription()
    {
        return null;
    }

    protected File getExe()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("BWAPATH");
        return new File(path, "bwa");
    }

    @Override
    public void createIndex(File fasta) throws PipelineJobException
    {
        _logger.info("Creating BWA index");

        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());

        args.add("index");
        args.add("-p");

        String outPrefix = FileUtil.getBaseName(fasta) + ".bwa.index";
        args.add(new File(fasta.getParentFile(), outPrefix).getPath());
        args.add(fasta.getPath());
        doExecute(getWorkingDir(fasta), args);
    }

    @Override
    public File doAlignment(File inputFastq, String basename, SequencePipelineSettings settings) throws PipelineJobException
    {
        return null;
    }
}
