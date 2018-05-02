package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 8/8/2014.
 */
public class GenotypeGVCFsWrapper extends AbstractGatkWrapper
{
    public GenotypeGVCFsWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, File outputFile, @Nullable List<String> options, boolean doCopyLocal, File... inputGVCFs) throws PipelineJobException
    {
        getLogger().info("Running GATK GenotypeGVCFs");

        ensureDictionary(referenceFasta);

        //ensure indexes
        this.ensureVCFIndexes(inputGVCFs);

        Set<File> toDelete = new HashSet<>();
        List<File> vcfsToProcess = new ArrayList<>();
        if (doCopyLocal)
        {
            getLogger().info("making local copies of gVCFs prior to genotyping");
            vcfsToProcess.addAll(copyVcfsLocally(Arrays.asList(inputGVCFs), toDelete, outputFile.getParentFile(), getLogger(), false));
        }
        else
        {
            vcfsToProcess.addAll(Arrays.asList(inputGVCFs));
        }

        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJavaFilepath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("GenotypeGVCFs");
        args.add("-R");
        args.add(referenceFasta.getPath());
        for (File gvcf : vcfsToProcess)
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

        if (!toDelete.isEmpty())
        {
            getLogger().info("deleting locally copied gVCFs");
            for (File f : toDelete)
            {
                f.delete();
            }
        }
    }

    public static List<File> copyVcfsLocally(Collection<File> inputGVCFs, Collection<File> toDelete, File localWorkDir, Logger log, boolean isResume) throws PipelineJobException
    {
        List<File> vcfsToProcess = new ArrayList<>();
        for (File f : inputGVCFs)
        {
            File origIdx = new File(f.getPath() + ".tbi");
            if (!origIdx.exists())
            {
                throw new PipelineJobException("expected index doesnt exist: " + origIdx.getPath());
            }

            File movedIdx = new File(localWorkDir, f.getName() + ".tbi");
            File movedVcf = new File(localWorkDir, f.getName());
            if (!isResume)
            {
                if (movedIdx.exists())
                {
                    log.debug("moved index exists, skipping file: " + f.getName());
                }
                else
                {
                    log.debug("copying file: " + f.getName());
                    try
                    {
                        if (movedVcf.exists())
                        {
                            movedVcf.delete();
                        }
                        FileUtils.copyFile(f, movedVcf);
                        FileUtils.copyFile(origIdx, movedIdx);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }

            toDelete.add(movedVcf);
            toDelete.add(movedIdx);

            vcfsToProcess.add(movedVcf);
        }

        return vcfsToProcess;
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
