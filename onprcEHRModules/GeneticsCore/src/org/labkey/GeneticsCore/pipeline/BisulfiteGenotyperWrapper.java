package org.labkey.GeneticsCore.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class BisulfiteGenotyperWrapper extends AbstractBisSnpWrapper
{
    public BisulfiteGenotyperWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File inputBam, File referenceFasta, File cpgOutputFile, File snpOutputFile, Integer maxThreads, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running BisSNP BisulfiteGenotyper for: " + inputBam.getName());

        if (cpgOutputFile.getName().toLowerCase().endsWith(".gz") || snpOutputFile.getName().toLowerCase().endsWith(".gz"))
        {
            throw new PipelineJobException("BisulfiteGenotyper is built using an old GATK version and does not work properly with gzipped files");
        }

        ensureDictionary(referenceFasta);

        File createdIndex = SequencePipelineService.get().ensureBamIndex(inputBam, getLogger(), false);
        if (createdIndex == null)
        {
            getLogger().debug("\tusing existing BAM index");
        }

        File rawCpgOutput = new File(getOutputDir(cpgOutputFile), SequencePipelineService.get().getUnzippedBaseName(cpgOutputFile.getName()) + ".cpg-raw.vcf");
        File rawSnpOutput = new File(getOutputDir(snpOutputFile), SequencePipelineService.get().getUnzippedBaseName(snpOutputFile.getName()) + ".snp-raw.vcf");

        List<String> args = new ArrayList<>();
        args.add(getJava6Filepath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("BisulfiteGenotyper");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-vfn1");
        args.add(rawCpgOutput.getPath());
        args.add("-vfn2");
        args.add(rawSnpOutput.getPath());

        if (options != null)
        {
            args.addAll(options);
        }

        if (maxThreads != null)
        {
            args.add("-nt");
            args.add(maxThreads.toString());
        }
        else
        {
            getLogger().debug("max threads not set");
        }

        execute(args);
        if (!rawCpgOutput.exists())
        {
            throw new PipelineJobException("Expected output not found: " + rawCpgOutput.getPath());
        }

        try
        {
            SequencePipelineService.get().sortROD(rawCpgOutput, getLogger(), 2);
            SequencePipelineService.get().sortROD(rawSnpOutput, getLogger(), 2);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        //then filter
        getLogger().info("filtering CpG VCF");
        List<String> filterArgs1 = new ArrayList<>();
        filterArgs1.add(getJava6Filepath());
        filterArgs1.addAll(SequencePipelineService.get().getJavaOpts());
        filterArgs1.add("-jar");
        filterArgs1.add(getJAR().getPath());
        filterArgs1.add("-T");
        filterArgs1.add("VCFpostprocess");
        filterArgs1.add("-R");
        filterArgs1.add(referenceFasta.getPath());
        filterArgs1.add("-oldVcf");
        filterArgs1.add(rawCpgOutput.getPath());
        filterArgs1.add("-newVcf");
        filterArgs1.add(cpgOutputFile.getPath());
        filterArgs1.add("-snpVcf");
        filterArgs1.add(rawCpgOutput.getPath());
        filterArgs1.add("-o");
        filterArgs1.add(getFilterSummary(cpgOutputFile).getPath());
        execute(filterArgs1);

        (new File(rawCpgOutput.getPath() + ".idx")).delete();
        rawCpgOutput.delete();

        getLogger().info("filtering CpG VCF");
        List<String> filterArgs2 = new ArrayList<>();
        filterArgs2.add(getJava6Filepath());
        filterArgs2.addAll(SequencePipelineService.get().getJavaOpts());
        filterArgs2.add("-jar");
        filterArgs2.add(getJAR().getPath());
        filterArgs2.add("-T");
        filterArgs2.add("VCFpostprocess");
        filterArgs2.add("-R");
        filterArgs2.add(referenceFasta.getPath());
        filterArgs2.add("-oldVcf");
        filterArgs2.add(rawSnpOutput.getPath());
        filterArgs2.add("-newVcf");
        filterArgs2.add(snpOutputFile.getPath());
        filterArgs2.add("-snpVcf");
        filterArgs2.add(rawSnpOutput.getPath());
        filterArgs2.add("-o");
        filterArgs2.add(getFilterSummary(snpOutputFile).getPath());
        execute(filterArgs2);

        (new File(rawSnpOutput.getPath() + ".idx")).delete();
        rawSnpOutput.delete();

        if (createdIndex != null)
        {
            getLogger().debug("\tdeleting temp BAM index: " + createdIndex.getPath());
            createdIndex.delete();
        }
    }

    public File getFilterSummary(File vcf)
    {
        return new File(getOutputDir(vcf), SequencePipelineService.get().getUnzippedBaseName(vcf.getName()) + ".summary.txt");
    }
}
