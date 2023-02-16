package org.labkey.sequenceanalysis.run.preprocessing;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.run.util.FastqcRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FastqcProcessingStep extends AbstractPipelineStep implements PreprocessingStep
{
    public FastqcProcessingStep (PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<PreprocessingStep>
    {
        public Provider()
        {
            super("FASTQC", "FASTQC", "FASTQC", "This step runs FASTQC on the FASTQs immediately prior to this step.  This can be useful to evaluate the state of reads after preprocessing steps.", Collections.emptyList(), null, null);
        }

        @Override
        public FastqcProcessingStep create(PipelineContext context)
        {
            return new FastqcProcessingStep(this, context);
        }
    }

    @Override
    public Output processInputFile(File inputFile, @Nullable File inputFile2, File outputDir) throws PipelineJobException
    {
        PreprocessingOutputImpl output = new PreprocessingOutputImpl(inputFile, inputFile2);

        FastqcRunner runner = new FastqcRunner(getPipelineCtx().getLogger());
        Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (threads != null)
            runner.setThreads(threads);

        runner.setCacheResults(true);

        List<File> inputs = new ArrayList<>();
        Map<File, String> labelMap = new HashMap<>();

        inputs.add(inputFile);
        labelMap.put(inputFile, inputFile.getName());

        if (inputFile2 != null)
        {
            inputs.add(inputFile2);
        }

        try
        {
            runner.execute(inputs, labelMap);

            for (File f : inputs)
            {
                File html = runner.getExpectedHtmlFile(inputFile);
                if (html.exists())
                {
                    output.addSequenceOutput(html, "FASTQC: " + f.getName(), "FASTQC Report", null, null, null, null);
                }
                else
                {
                    getPipelineCtx().getLogger().error("Unable to find FASTQC report: " + html.getPath());
                }
            }

        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.setProcessedFastq(Pair.of(inputFile, inputFile2));

        return output;
    }
}
