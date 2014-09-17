package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.BamProcessingStep;
import org.labkey.sequenceanalysis.api.pipeline.DefaultPipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceGenome;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.run.util.IndelRealignerWrapper;

import java.io.File;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:59 PM
 */
public class IndelRealignerStep extends AbstractCommandPipelineStep<IndelRealignerWrapper> implements BamProcessingStep
{
    public IndelRealignerStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new IndelRealignerWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<IndelRealignerStep>
    {
        public Provider()
        {
            super("IndelRealigner", "Indel Realigner", "GATK", "The step runs GATK's IndelRealigner tool.  This tools performs local realignment to minmize the number of mismatching bases across all the reads.", null, null, "http://www.broadinstitute.org/gatk/gatkdocs/org_broadinstitute_sting_gatk_walkers_indels_IndelRealigner.html");
        }

        @Override
        public IndelRealignerStep create(PipelineContext ctx)
        {
            return new IndelRealignerStep(this, ctx);
        }
    }

    @Override
    public Output processBam(ReadsetModel rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File dictionary = new File(referenceGenome.getFastaFile().getParentFile(), FileUtil.getBaseName(referenceGenome.getFastaFile().getName()) + ".dict");
        boolean dictionaryExists = dictionary.exists();
        getPipelineCtx().getLogger().debug("dict exists: " + dictionaryExists + ", " + dictionary.getPath());

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".realigned.bam");
        output.setBAM(getWrapper().execute(inputBam, outputBam, referenceGenome.getFastaFile(), null));
        output.addIntermediateFile(outputBam);
        output.addIntermediateFile(getWrapper().getExpectedIntervalsFile(inputBam), "Realigner Intervals File");

        if (!dictionaryExists && dictionary.exists())
        {
            output.addIntermediateFile(dictionary);
        }

        //note: we might sort the input
        File sortedBam = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + ".sorted.bam");
        if (sortedBam.exists())
        {
            getPipelineCtx().getLogger().debug("sorted file exists: " + sortedBam.getPath());
            output.addIntermediateFile(sortedBam);
            output.addIntermediateFile(new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".realigned.bai"));
        }
        else
        {
            getPipelineCtx().getLogger().debug("sorted file does not exist: " + sortedBam.getPath());
            output.addIntermediateFile(new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".realigned.bai"));
        }

        return output;
    }
}
