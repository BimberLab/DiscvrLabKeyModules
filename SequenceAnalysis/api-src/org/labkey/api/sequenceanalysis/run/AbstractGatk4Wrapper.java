package org.labkey.api.sequenceanalysis.run;

import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
abstract public class AbstractGatk4Wrapper extends AbstractCommandWrapper
{
    protected Integer _maxRamOverride = null;

    public AbstractGatk4Wrapper(Logger log)
    {
        super(log);
    }

    protected String getJarName()
    {
        return "GenomeAnalysisTK4.jar";
    }

    public File getJAR()
    {
        return getJAR(true);
    }

    public File getJAR(boolean throwIfNotFound)
    {
        return resolveFileInPath(getJarName(), getPackageName(), throwIfNotFound);
    }

    public void setMaxRamOverride(Integer maxRamOverride)
    {
        _maxRamOverride = maxRamOverride;
    }

    public boolean jarExists()
    {
        return getJAR(false) != null;
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
        args.addAll(SequencePipelineService.get().getJavaOpts(_maxRamOverride));
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("--version");

        return StringUtils.trimToNull(executeWithOutput(args));
    }

    public List<String> getBaseArgs()
    {
        return getBaseArgs(null);
    }

    protected String getPackageName()
    {
        return "GATKPATH";
    }

    public List<String> getBaseArgs(@Nullable String toolName)
    {
        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJavaFilepath());
        args.addAll(SequencePipelineService.get().getJavaOpts(_maxRamOverride));
        args.add("-jar");
        args.add(getJAR().getPath());

        if (toolName != null)
        {
            args.add(toolName);
        }

        return args;
    }

    public static List<String> generateIntervalArgsForFullGenome(ReferenceGenome rg, File intervalFile) throws PipelineJobException
    {
        try (PrintWriter writer = PrintWriters.getPrintWriter(intervalFile))
        {
            SAMSequenceDictionaryExtractor.extractDictionary(rg.getSequenceDictionary().toPath()).getSequences().forEach(x -> writer.println(x.getSequenceName()));
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        List<String> ret = new ArrayList<>();
        ret.add("-L");
        ret.add(intervalFile.getPath());

        return ret;
    }
}
