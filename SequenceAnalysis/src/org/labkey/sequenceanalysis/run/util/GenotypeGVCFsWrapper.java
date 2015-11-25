package org.labkey.sequenceanalysis.run.util;

import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.variant.vcf.VCFCodec;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
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
        for (File gvcf : inputGVCFs)
        {
            File expectedIdx = new File(gvcf.getPath() + ".tbi");
            if (!expectedIdx.exists())
            {
                expectedIdx = new File(gvcf.getPath() + ".idx");
                if (!expectedIdx.exists())
                {
                    try
                    {
                        getLogger().info("index not found, creating: " + gvcf);
                        Index idx = IndexFactory.createIndex(gvcf, new VCFCodec(), IndexFactory.IndexType.LINEAR);
                        IndexFactory.writeIndex(idx, expectedIdx);
                        //TabixRunner r = new TabixRunner(getLogger());
                        //r.execute(gvcf);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }
        }

        List<String> args = new ArrayList<>();
        args.add("java");
        args.addAll(getBaseParams());
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

        Integer maxThreads = SequenceTaskHelper.getMaxThreads(getLogger());
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
}
