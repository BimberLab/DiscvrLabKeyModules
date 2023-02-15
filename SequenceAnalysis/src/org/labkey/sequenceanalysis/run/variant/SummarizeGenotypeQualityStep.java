package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.DISCVRSeqRunner;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class SummarizeGenotypeQualityStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public SummarizeGenotypeQualityStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SummarizeGenotypeQualityStep> implements RequiresPedigree
    {
        public Provider()
        {
            super("SummarizeGenotypeQuality", "Summarize Genotype Quality", "DISCVRseq", "This produces a TSV report summarizing genotype qualities by Genotype Type", Arrays.asList(
                    ToolParameterDescriptor.create("excludeFiltered", "Exclude Filtered", "If selected, filtered sites will be ignored.", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, null)
                ), null, "");
        }

        public SummarizeGenotypeQualityStep create(PipelineContext ctx)
        {
            return new SummarizeGenotypeQualityStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        DISCVRSeqRunner wrapper = new DISCVRSeqRunner(getPipelineCtx().getLogger());
        List<String> args = new ArrayList<>(wrapper.getBaseArgs("SummarizeGenotypeQuality"));

        if (getProvider().getParameterByName("excludeFiltered").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false))
        {
            args.add("--excludeFiltered");
        }

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                args.add("-L");
                args.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        args.add("-V");
        args.add(inputVCF.getPath());

        File outputTable = new File(outputDirectory, SequencePipelineService.get().getUnzippedBaseName(inputVCF.getName()) + ".gq.txt");
        args.add("-O");
        args.add(outputTable.getPath());

        wrapper.execute(args);

        output.addInput(inputVCF, "Input VCF");
        output.addOutput(outputTable, "Genotype Quality Summary");

        output.addSequenceOutput(outputTable, "Genotype Quality Summary for: " + inputVCF.getName(), "Genotype Quality Summary", null, null, genome.getGenomeId(), null);

        return output;
    }
}
