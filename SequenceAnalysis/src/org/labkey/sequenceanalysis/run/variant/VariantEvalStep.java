package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.VariantEvalWrapper;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class VariantEvalStep extends AbstractCommandPipelineStep<VariantEvalWrapper> implements VariantProcessingStep
{
    public VariantEvalStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new VariantEvalWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantEvalStep>
    {
        public Provider()
        {
            super("VariantEvalStep", "GATK VariantEval", "GATK", "Generates a table of summary data from the final VCF.", null, null, "");
        }

        @Override
        public VariantEvalStep create(PipelineContext ctx)
        {
            return new VariantEvalStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        File outputFile = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".eval.grp");
        getWrapper().executeEval(genome.getWorkingFastaFile(), inputVCF, outputFile, "Set1", intervals);

        output.addOutput(outputFile, "Variant Eval Output");
        
        return output;
    }
}
