package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.resource.FileResource;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import org.labkey.api.util.Path;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class HaplotypeCallerWrapper extends AbstractGatkWrapper
{
    private boolean _multiThreaded = false;

    public HaplotypeCallerWrapper(Logger log)
    {
        super(log);
    }

    public void setMultiThreaded(boolean multiThreaded)
    {
        _multiThreaded = multiThreaded;
    }

    public void execute(File inputBam, File referenceFasta, File outputFile, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running GATK HaplotypeCaller for: " + inputBam.getName());

        ensureDictionary(referenceFasta);

        File expectedIndex = new File(inputBam.getPath() + ".bai");
        boolean doDeleteIndex = false;
        if (!expectedIndex.exists())
        {
            getLogger().debug("\tcreating temp index for BAM: " + inputBam.getName());
            new BuildBamIndexWrapper(getLogger()).executeCommand(inputBam);

            doDeleteIndex = true;
        }
        else
        {
            getLogger().debug("\tusing existing index: " + expectedIndex.getPath());
        }

        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJavaFilepath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("HaplotypeCaller");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-o");
        args.add(outputFile.getPath());
        if (options != null)
        {
            args.addAll(options);
        }
        args.add("-A");
        args.add("DepthPerSampleHC");
        args.add("-A");
        args.add("HomopolymerRun");

        args.add("--max_alternate_alleles");
        args.add("12");

        if (_multiThreaded)
        {
            getLogger().debug("checking available threads");
            Integer maxThreads = SequenceTaskHelper.getMaxThreads(getLogger());
            if (maxThreads != null)
            {
                args.add("-nct");
                args.add(maxThreads.toString());
            }
            else
            {
                getLogger().debug("max threads not set");
            }
        }
        else
        {
            getLogger().debug("multithreading not enabled");
        }

        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
        }

        if (doDeleteIndex)
        {
            getLogger().debug("\tdeleting temp BAM index: " + expectedIndex.getPath());
            expectedIndex.delete();
        }
    }

    public void executeWithQueue(File inputBam, File referenceFasta, File outputFile, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running GATK HaplotypeCaller using Queue for: " + inputBam.getName());

        ensureDictionary(referenceFasta);

        File expectedIndex = new File(inputBam.getPath() + ".bai");
        boolean doDeleteIndex = false;
        if (!expectedIndex.exists())
        {
            getLogger().debug("\tcreating temp index for BAM: " + inputBam.getName());
            new BuildBamIndexWrapper(getLogger()).executeCommand(inputBam);

            doDeleteIndex = true;
        }
        else
        {
            getLogger().debug("\tusing existing index: " + expectedIndex.getPath());
        }

        try
        {
            Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
            FileResource r = (FileResource)module.getModuleResolver().lookup(Path.parse("external/qscript/HaplotypeCallerRunner.scala"));
            File scalaScript = r.getFile();

            if (scalaScript == null)
                throw new FileNotFoundException("Not found: " + scalaScript);

            if (!scalaScript.exists())
                throw new FileNotFoundException("Not found: " + scalaScript.getPath());

            List<String> args = new ArrayList<>();
            args.add(SequencePipelineService.get().getJavaFilepath());
            //for now, ignore java opts since queue's scatter/gather causes issues
            //args.addAll(SequencePipelineService.get().getJavaOpts());
            args.add("-classpath");
            args.add(getJAR().getPath());

            args.add("-jar");
            args.add(getQueueJAR().getPath());
            args.add("-S");
            args.add(scalaScript.getPath());
            args.add("-jobRunner");
            args.add("ParallelShell");
            args.add("-run");

            args.add("-R");
            args.add(referenceFasta.getPath());
            args.add("-I");
            args.add(inputBam.getPath());
            args.add("-o");
            args.add(outputFile.getPath());
            if (options != null)
            {
                args.addAll(options);
            }
            args.add("-A");
            args.add("DepthPerSampleHC");
            args.add("-A");
            args.add("HomopolymerRun");
            args.add("--max_alternate_alleles");
            args.add("12");

            args.add("-startFromScratch");
            args.add("-scatterCount");
            Integer maxThreads = SequenceTaskHelper.getMaxThreads(getLogger());
            if (maxThreads != null)
            {
                args.add(maxThreads.toString());
            }
            else
            {
                args.add("1");
            }

            execute(args);
            if (!outputFile.exists())
            {
                throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
            }

            if (doDeleteIndex)
            {
                getLogger().debug("\tdeleting temp BAM index: " + expectedIndex.getPath());
                expectedIndex.delete();
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
