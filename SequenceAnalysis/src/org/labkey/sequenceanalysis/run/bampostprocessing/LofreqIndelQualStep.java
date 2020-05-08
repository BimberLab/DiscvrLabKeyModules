package org.labkey.sequenceanalysis.run.bampostprocessing;

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
import org.labkey.sequenceanalysis.run.analysis.LofreqAnalysis;

import java.io.File;
import java.util.Collections;

public class LofreqIndelQualStep extends AbstractCommandPipelineStep<LofreqAnalysis.LofreqWrapper> implements BamProcessingStep
{
    public LofreqIndelQualStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new LofreqAnalysis.LofreqWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<LofreqIndelQualStep>
    {
        public Provider()
        {
            super("LofreqIndelQual", "Lofreq Indelqual", "Lofreq", "This runs lofreq indelqual, which adds indel qualities necessary to call somatic indels using lofreq", Collections.emptyList(), null, "http://csb5.github.io/lofreq/");
        }

        @Override
        public LofreqIndelQualStep create(PipelineContext ctx)
        {
            return new LofreqIndelQualStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".lofreqindel.bam");
        output.addIntermediateFile(outputBam);
        output.setBAM(getWrapper().addIndelQuals(inputBam, outputBam, referenceGenome.getWorkingFastaFile()));

        SequencePipelineService.get().ensureBamIndex(outputBam, getPipelineCtx().getLogger(), false);

        return output;
    }
}
