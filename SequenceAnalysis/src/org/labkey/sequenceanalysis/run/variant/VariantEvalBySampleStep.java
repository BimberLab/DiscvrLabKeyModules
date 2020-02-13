package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.jetbrains.annotations.Nullable;
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

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class VariantEvalBySampleStep extends AbstractCommandPipelineStep<VariantEvalWrapper> implements VariantProcessingStep
{
    public VariantEvalBySampleStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new VariantEvalWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantEvalBySampleStep>
    {
        public Provider()
        {
            super("VariantEvalBySampleStep", "GATK VariantEval By Sample", "GATK", "Generates a table of summary data from the final VCF.", null, null, "");
        }

        public VariantEvalBySampleStep create(PipelineContext ctx)
        {
            return new VariantEvalBySampleStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        File outputFile = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".eval.sample.grp");
        getWrapper().executeEvalBySample(genome.getWorkingFastaFile(), inputVCF, outputFile, "Set1", intervals);

        output.addOutput(outputFile, "Variant Eval By Sample Output");

        return output;
    }
}
