package org.labkey.sequenceanalysis.run.preprocessing;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.run.util.FlashWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FlashPipelineStep extends AbstractCommandPipelineStep<FlashWrapper> implements PreprocessingStep
{
    public FlashPipelineStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new FlashWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<PreprocessingStep>
    {
        public Provider()
        {
            super("Flash", "Merge Paired Reads (Flash)", "Flash", "This step merges reads from a pair of FASTQs into a single merged read (if possible).", List.of(), null, "https://ccb.jhu.edu/software/FLASH/");
        }

        @Override
        public FlashPipelineStep create(PipelineContext context)
        {
            return new FlashPipelineStep(this, context);
        }
    }

    @Override
    public Output processInputFile(File inputFile, @Nullable File inputFile2, File outputDir) throws PipelineJobException
    {
        PreprocessingOutputImpl output = new PreprocessingOutputImpl(inputFile, inputFile2);

        List<String> extraArgs = new ArrayList<>();
        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (maxThreads != null)
        {
            extraArgs.add("-t");
            extraArgs.add(maxThreads.toString());
        }

        File merged = getWrapper().execute(inputFile, inputFile2, outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputFile.getName()), extraArgs);
        if (!merged.exists())
        {
            throw new PipelineJobException("Unable to find merged output: " + merged.getPath());
        }
        output.setProcessedFastq(Pair.of(merged, null));

        for (String suffix : Arrays.asList("notCombined_1.fastq.gz", "notCombined_2.fastq.gz", "hist", "histogram"))
        {
            File t = new File(merged.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(inputFile.getName()) + "." + suffix);
            if (t.exists())
            {
                output.addIntermediateFile(t);
            }
        }

        return output;
    }
}
