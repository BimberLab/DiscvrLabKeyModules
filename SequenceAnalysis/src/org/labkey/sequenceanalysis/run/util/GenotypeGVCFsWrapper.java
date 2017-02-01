package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.resource.FileResource;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import org.labkey.api.util.Path;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class GenotypeGVCFsWrapper extends AbstractGatkWrapper
{
    public GenotypeGVCFsWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, File outputFile, @Nullable List<String> options, File... inputGVCFs) throws PipelineJobException
    {
        getLogger().info("Running GATK GenotypeGVCFs");

        ensureDictionary(referenceFasta);

        if (inputGVCFs.length > 200)
        {
            getLogger().info("merging gVCF files prior to genotyping");
            //TODO
        }

        //ensure indexes
        this.ensureVCFIndexes(inputGVCFs);

        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJavaFilepath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("GenotypeGVCFs");
        args.add("-R");
        args.add(referenceFasta.getPath());
        for (File gvcf : inputGVCFs)
        {
            args.add("--variant");
            args.add(gvcf.getPath());
        }

        args.add("-o");
        args.add(outputFile.getPath());
        args.add("-nda");

        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
        if (maxThreads != null)
        {
            args.add("-nt");
            args.add(maxThreads.toString());
        }

        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
        }
    }

    public void executeWithQueue(File referenceFasta, File outputFile, @Nullable List<String> options, File... inputGVCFs) throws PipelineJobException
    {
        getLogger().info("Running GATK GenotypeGVCFs using Queue");

        ensureDictionary(referenceFasta);

        //ensure indexes
        this.ensureVCFIndexes(inputGVCFs);

        try
        {
            Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
            FileResource r = (FileResource)module.getModuleResolver().lookup(Path.parse("external/qscript/GenotypeGVCFsRunner.scala"));
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

            for (File gvcf : inputGVCFs)
            {
                args.add("-V");
                args.add(gvcf.getPath());
            }

            args.add("-o");
            args.add(outputFile.getPath());

            args.add("-nda");

            if (options != null)
            {
                args.addAll(options);
            }

            args.add("-startFromScratch");
            args.add("-scatterCount");
            args.add(getScatterForQueueJob().toString());

            execute(args);
            if (!outputFile.exists())
            {
                throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private void ensureVCFIndexes(File[] inputGVCFs) throws PipelineJobException
    {
        for (File gvcf : inputGVCFs)
        {
            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(gvcf, getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
