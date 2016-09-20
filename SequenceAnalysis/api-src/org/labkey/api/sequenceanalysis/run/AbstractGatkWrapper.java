package org.labkey.api.sequenceanalysis.run;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
abstract public class AbstractGatkWrapper extends AbstractCommandWrapper
{
    public AbstractGatkWrapper(Logger log)
    {
        super(log);
    }

    protected File getJAR()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("GATKPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("GenomeAnalysisTK.jar") : new File(path, "GenomeAnalysisTK.jar");
    }

    protected File getQueueJAR()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("GATKPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("Queue.jar") : new File(path, "Queue.jar");
    }

    public boolean jarExists()
    {
        return getJAR() == null || !getJAR().exists();
    }

    protected void ensureDictionary(File referenceFasta) throws PipelineJobException
    {
        getLogger().info("\tensure fasta index exists");
        SequenceAnalysisService.get().ensureFastaIndex(referenceFasta, getLogger());

        getLogger().info("\tensure dictionary exists");
        new CreateSequenceDictionaryWrapper(getLogger()).execute(referenceFasta, false);
    }

    public String getVersionString() throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJavaFilepath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("--version");

        return StringUtils.trimToNull(executeWithOutput(args));
    }
}
