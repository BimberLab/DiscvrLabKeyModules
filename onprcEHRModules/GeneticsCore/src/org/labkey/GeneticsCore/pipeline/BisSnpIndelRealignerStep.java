package org.labkey.GeneticsCore.pipeline;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:59 PM
 */
public class BisSnpIndelRealignerStep extends AbstractCommandPipelineStep<BisSnpIndelRealignerWrapper> implements BamProcessingStep
{
    public BisSnpIndelRealignerStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new BisSnpIndelRealignerWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<BisSnpIndelRealignerStep>
    {
        public Provider()
        {
            super("BisSnpIndelRealigner", "BisSNP Indel Realigner", "BisSNP", "The step runs BisSNP's IndelRealigner tool.  It is similar to GATK's, except adapted for bisulfite data.", Arrays.asList(

            ), null, "https://sourceforge.net/projects/bissnp/");
        }

        @Override
        public BisSnpIndelRealignerStep create(PipelineContext ctx)
        {
            return new BisSnpIndelRealignerStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File dictionary = new File(referenceGenome.getWorkingFastaFile().getParentFile(), FileUtil.getBaseName(referenceGenome.getWorkingFastaFile().getName()) + ".dict");
        boolean dictionaryExists = dictionary.exists();
        getPipelineCtx().getLogger().debug("dict exists: " + dictionaryExists + ", " + dictionary.getPath());

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".bissnp-realigned.bam");
        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getJob().getLogger());
        output.setBAM(getWrapper().execute(inputBam, outputBam, referenceGenome.getWorkingFastaFile(), null, maxThreads));

        output.addIntermediateFile(outputBam);
        output.addIntermediateFile(getWrapper().getExpectedIntervalsFile(inputBam), "Realigner Intervals File");

        if (!dictionaryExists)
        {
            if (dictionary.exists())
            {
                output.addIntermediateFile(dictionary);
            }
            else
            {
                getPipelineCtx().getLogger().debug("dict file not found: " + dictionary.getPath());
            }
        }

        //note: we might sort the input
        File sortedBam = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + ".sorted.bam");
        if (sortedBam.exists())
        {
            getPipelineCtx().getLogger().debug("sorted file exists: " + sortedBam.getPath());
            output.addIntermediateFile(sortedBam);
            output.addIntermediateFile(new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".bissnp-realigned.bai"));
        }
        else
        {
            getPipelineCtx().getLogger().debug("sorted file does not exist: " + sortedBam.getPath());
            output.addIntermediateFile(new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".bissnp-realigned.bai"));
        }

        return output;
    }
}
