package org.labkey.api.sequenceanalysis.run;

import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
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

        return path == null ? new File(getJarName()) : new File(path, getJarName());
    }

    public void setMaxRamOverride(Integer maxRamOverride)
    {
        _maxRamOverride = maxRamOverride;
    }

    protected void addJavaHomeToEnvironment()
    {
        //since GATK requires java8, set JAVA_HOME to match this:
        File java8 = new File(SequencePipelineService.get().getJava8FilePath()).getParentFile();
        if (java8.getParentFile() == null)
        {
            getLogger().debug("unexpected path to java8, cannot determine JAVA_HOME: " + java8.getPath());
            return;
        }

        String javaDir = java8.getParentFile().getPath();
        getLogger().debug("setting JAVA_HOME to java8 location: " + javaDir);
        addToEnvironment("JAVA_HOME", javaDir);
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
        args.add(SequencePipelineService.get().getJava8FilePath());
        args.addAll(SequencePipelineService.get().getJavaOpts(_maxRamOverride));
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("--version");

        return StringUtils.trimToNull(executeWithOutput(args));
    }

    protected List<String> getBaseArgs()
    {
        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJava8FilePath());
        args.addAll(SequencePipelineService.get().getJavaOpts(_maxRamOverride));
        args.add("-jar");
        args.add(getJAR().getPath());

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
