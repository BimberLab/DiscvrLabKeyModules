package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 4/2/2017.
 */
public class CombineGVCFsWrapper extends AbstractGatk4Wrapper
{
    public CombineGVCFsWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, File outputFile, @Nullable List<String> options, File... inputGVCFs) throws PipelineJobException
    {
        getLogger().info("Running GATK 4 CombineGVCFs");

        ensureDictionary(referenceFasta);

        //ensure indexes
        this.ensureVCFIndexes(inputGVCFs);

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("CombineGVCFs");
        args.add("-R");
        args.add(referenceFasta.getPath());
        for (File gvcf : inputGVCFs)
        {
            args.add("--variant");
            args.add(gvcf.getPath());
        }

        args.add("--ignore-variants-starting-outside-interval");
        args.add("-O");
        args.add(outputFile.getPath());

        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
        }

        this.ensureVCFIndexes(new File[]{outputFile});
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
