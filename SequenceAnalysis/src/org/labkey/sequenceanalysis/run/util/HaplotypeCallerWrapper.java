package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.sequenceanalysis.pipeline.ReblockGvcfHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class HaplotypeCallerWrapper extends AbstractGatk4Wrapper
{
    public HaplotypeCallerWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File inputBam, File referenceFasta, File outputFile, List<String> options) throws PipelineJobException
    {
        execute(inputBam, referenceFasta, outputFile, options, true);
    }

    public void execute(File inputBam, File referenceFasta, File outputFile, List<String> options, boolean reblockGVCF) throws PipelineJobException
    {
        getLogger().info("Running GATK 4 HaplotypeCaller for: " + inputBam.getName());

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

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("HaplotypeCaller");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-O");
        args.add(outputFile.getPath());
        if (options != null)
        {
            args.addAll(options);
        }

        args.add("-ERC");
        args.add("GVCF");

        args.add("-A");
        args.add("DepthPerSampleHC");

        args.add("--max-alternate-alleles");
        args.add("12");

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

        if (reblockGVCF)
        {
            getLogger().info("Running GATK 4 ReblockGVCF for: " + outputFile.getName());

            File reblockOutput = new File(outputFile.getParentFile(), "tempReblock.g.vcf.gz");

            new ReblockGvcfHandler.ReblockGvcfWrapper(getLogger()).execute(outputFile, reblockOutput, referenceFasta);

            File outputFileIdx = new File(outputFile.getPath() + ".tbi");

            getLogger().debug("Replacing original gVCF with reblocked file");
            outputFileIdx.delete();
            outputFile.delete();

            File reblockOutputIdx = new File(reblockOutput.getPath() + ".tbi");
            try
            {
                FileUtils.moveFile(reblockOutput, outputFile);
                FileUtils.moveFile(reblockOutputIdx, outputFileIdx);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
