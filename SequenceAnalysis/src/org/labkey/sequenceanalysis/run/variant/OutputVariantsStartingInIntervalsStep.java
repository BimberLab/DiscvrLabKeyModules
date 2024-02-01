package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class OutputVariantsStartingInIntervalsStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public OutputVariantsStartingInIntervalsStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<OutputVariantsStartingInIntervalsStep> implements RequiresPedigree
    {
        public Provider()
        {
            super("OutputVariantsStartingInIntervals", "Output Variants Starting In Intervals", "DISCVRseq", "This will subset the VCF to include only variants the start within the target intervals", List.of(), null, "https://bimberlab.github.io/DISCVRSeq/");
        }

        @Override
        public OutputVariantsStartingInIntervalsStep create(PipelineContext ctx)
        {
            return new OutputVariantsStartingInIntervalsStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();
        if (intervals == null)
        {
            throw new PipelineJobException("This step requires intervals");
        }

        File outputFile = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".subset.vcf.gz");
        Wrapper wrapper = new Wrapper(getPipelineCtx().getLogger());
        wrapper.execute(inputVCF, outputFile, intervals);

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");
        output.addOutput(outputFile, "Subset VCF");

        output.setVcf(outputFile);

        return output;
    }

    public static class Wrapper extends AbstractDiscvrSeqWrapper
    {
        public Wrapper(Logger log)
        {
            super(log);
        }

        public void execute(File inputVcf, File outputVcf, List<Interval> intervals) throws PipelineJobException
        {
            File intervalFile = new File(outputVcf.getParentFile(), "scatterIntervals.list");
            try (PrintWriter writer = PrintWriters.getPrintWriter(intervalFile))
            {
                intervals.forEach(interval -> {
                    writer.println(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                });
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            List<String> args = new ArrayList<>(getBaseArgs());
            args.add("OutputVariantsStartingInIntervals");

            args.add("-V");
            args.add(inputVcf.getPath());

            args.add("-O");
            args.add(outputVcf.getPath());

            args.add("-L");
            args.add(intervalFile.getPath());

            execute(args);
            if (!outputVcf.exists())
            {
                throw new PipelineJobException("Missing file: " + outputVcf.getPath());
            }

            intervalFile.delete();
        }
    }
}
