package org.labkey.sequenceanalysis.run.preprocessing;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FastpStep extends AbstractCommandPipelineStep<FastpStep.FastpWrapper> implements PreprocessingStep
{
    public FastpStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new FastpWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<PreprocessingStep>
    {
        public Provider()
        {
            super("Fastp", "Fastp Preprocessing", "Fastp", "This provides the ability to trim adapters from the 5' and/or 3' end of the reads.  5' trimming will always be performed first, followed by a second round if 3' adapter trimming was selected.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--trim_poly_g"), "trim_poly_g", "Trim polyG stretches", "Trim polyG stretches. This is critical for Illumina data", "checkbox", new JSONObject()
                    {{

                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--trim_poly_x"), "trim_poly_x", "Trim polyX stretches", "Enable polyX trimming in 3' ends", "checkbox", new JSONObject()
                    {{

                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--detect_adapter_for_pe"), "detect_adapter_for_pe", "Detect Adapter For PE", "Enable adapter detection for PE data to get ultra-clean data. It takes more time to find just a little bit more adapters", "checkbox", new JSONObject()
                    {{

                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--qualified_quality_phred"), "qualified_quality_phred", "Qualified Quality PHRED", "The quality value that a base is qualified. Default 15 means phred quality >=Q15 is qualified", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 15),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--length_required"), "length_required", "Length Required", "Min read length required", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 36),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--cut_tail"), "cut_tail", "Cut Tail", "Move a sliding window from tail (3') to front, drop the bases in the window if its mean quality is below cut_mean_quality, stop otherwise. Default is disabled.", "checkbox", new JSONObject()
                    {{

                    }}, false),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--cut_mean_quality"), "cut_mean_quality", "Cut Mean Quality", "Min read length required", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 20)
            ), null, "https://github.com/opengene/fastp");
        }

        @Override
        public FastpStep create(PipelineContext context)
        {
            return new FastpStep(this, context);
        }
    }

    @Override
    public Output processInputFile(File inputFile, @Nullable File inputFile2, File outputDir) throws PipelineJobException
    {
        PreprocessingOutputImpl output = new PreprocessingOutputImpl(inputFile, inputFile2);

        List<String> args = new ArrayList<>();
        args.add("--in1");
        args.add(inputFile.getPath());

        if (inputFile2 != null)
        {
            args.add("--in2");
            args.add(inputFile2.getPath());
        }

        File outputFile1 = FileUtil.appendName(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputFile.getName()) + ".fastp.fastq.gz");
        args.add("--out1");
        args.add(outputFile1.getPath());

        File outputFile2 = inputFile2 == null ? null : FileUtil.appendName(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputFile2.getName()) + ".fastp.fastq.gz");
        if (inputFile2 != null)
        {
            args.add("--out2");
            args.add(outputFile2.getPath());
        }

        File json = FileUtil.appendName(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputFile.getName()) + ".json");
        args.add("--json");
        args.add(json.getPath());

        File html = FileUtil.appendName(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputFile.getName()) + ".html");
        args.add("--html");
        args.add(html.getPath());

        args.addAll(getClientCommandArgs());

        getWrapper().execute(args);

        output.setProcessedFastq(Pair.of(outputFile1, outputFile2));
        output.addCommandsExecuted(getWrapper().getCommandsExecuted());

        return output;
    }

    public static class FastpWrapper extends AbstractCommandWrapper
    {
        public FastpWrapper(Logger logger)
        {
            super(logger);
        }

        public void run(List<String> extraParams) throws PipelineJobException
        {
            List<String> params = new ArrayList<>();
            File fastp = AbstractCommandWrapper.resolveFileInPath("fastp", null, true);
            params.add(fastp.getAbsolutePath());

            params.addAll(extraParams);

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
            if (maxThreads != null)
            {
                params.add("--thread");
                params.add(maxThreads.toString());
            }

            execute(params);
        }
    }
}
