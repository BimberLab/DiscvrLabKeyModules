package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsIndexer;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.sequenceanalysis.pipeline.ReblockGvcfHandler;
import org.labkey.sequenceanalysis.util.SequenceUtil;

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

    public void execute(File inputBamOrCram, File referenceFasta, File outputFile, List<String> options) throws PipelineJobException
    {
        execute(inputBamOrCram, referenceFasta, outputFile, options, true);
    }

    public void execute(File inputBamOrCram, File referenceFasta, File outputFile, List<String> options, boolean reblockGVCF) throws PipelineJobException
    {
        getLogger().info("Running GATK 4 HaplotypeCaller for: " + inputBamOrCram.getName());

        ensureDictionary(referenceFasta);

        File expectedIndex = SequenceUtil.getExpectedIndex(inputBamOrCram);
        if (!expectedIndex.exists())
        {
            getLogger().debug("\tcreating index for BAM: " + inputBamOrCram.getName());
            new SamtoolsIndexer(getLogger()).execute(inputBamOrCram);
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
        args.add(inputBamOrCram.getPath());
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

        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
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
