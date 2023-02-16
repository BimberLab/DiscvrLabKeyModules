package org.labkey.sequenceanalysis.run.preprocessing;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
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
import org.labkey.api.sequenceanalysis.run.DISCVRSeqRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrintReadsContainingStep extends AbstractCommandPipelineStep<PrintReadsContainingStep.Wrapper> implements PreprocessingStep
{
        public PrintReadsContainingStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new PrintReadsContainingStep.Wrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<PreprocessingStep>
    {
        public Provider()
        {
            super("PrintReadsContaining", "Filter Reads By Sequence Motifs", "PrintReadsContaining", "This step filters input reads and will output only reads containing the provided sequence(s).", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--matchAllExpressions"), "matchAllExpressions", "Match All Expressions", "If checked, the sequence must match all expressions.", "checkbox", null, false),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--editDistance"), "editDistance", "Edit Distance", "If provided, the tool will perform fuzzy matching, allowing hits with up to this many mismatches.  Be aware, if this is used, the query expression must be bases (ATCG) only.", "ldk-integerfield", null, null),
                    ToolParameterDescriptor.create("readExpressions", "Read Expressions (either)", "The list of expressions to test, one per line.  Expressions can be simple strings or a java regular expression.  The default is to retain a read pair where either reads matches at least one of these.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("replaceAllWhitespace", false);
                        put("width", 400);
                    }}, null),
                    ToolParameterDescriptor.create("read1Expressions", "Read Expressions (forward)", "The list of expressions to test in read1, one per line.  Expressions can be simple strings or a java regular expression.  The default is to retain a read pair where read1 matches any of these.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("replaceAllWhitespace", false);
                        put("width", 400);
                    }}, null),
                    ToolParameterDescriptor.create("read2Expressions", "Read Expressions (reverse)", "The list of expressions to test in read2, one per line.  Expressions can be simple strings or a java regular expression.  The default is to retain a read pair where read2 matches any of these.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("replaceAllWhitespace", false);
                        put("width", 400);
                    }}, null)
            ), PageFlowUtil.set("/sequenceanalysis/field/TrimmingTextArea.js"), "https://github.com/BimberLab/DISCVRSeq");
        }

        @Override
        public PrintReadsContainingStep create(PipelineContext context)
        {
            return new PrintReadsContainingStep(this, context);
        }
    }

    private List<String> addExpressionArgs(String param, String argName)
    {
        List<String> ret = new ArrayList<>();
        param = StringUtils.trimToNull(param);
        if (param != null)
        {
            String[] values = param.split(";");
            for (String value : values)
            {
                ret.add(argName);
                ret.add(value);
            }
        }

        return ret;
    }

    @Override
    public PreprocessingStep.Output processInputFile(File inputFile, @Nullable File inputFile2, File outputDir) throws PipelineJobException
    {
        PreprocessingOutputImpl output = new PreprocessingOutputImpl(inputFile, inputFile2);

        List<String> extraArgs = new ArrayList<>();
        extraArgs.addAll(getClientCommandArgs());

        extraArgs.addAll(addExpressionArgs(getProvider().getParameterByName("readExpressions").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class), "-e"));
        extraArgs.addAll(addExpressionArgs(getProvider().getParameterByName("read1Expressions").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class), "-e1"));
        extraArgs.addAll(addExpressionArgs(getProvider().getParameterByName("read2Expressions").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class), "-e2"));

        File output1 = new File(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputFile.getName()) + ".filtered.fastq.gz");
        File output2 = inputFile2 == null ? null : new File(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputFile2.getName()) + ".filtered.fastq.gz");
        File summary = new File(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputFile.getName()) + ".summary.txt");

        extraArgs.add("--summaryFile");
        extraArgs.add(summary.getPath());

        Pair<File, File> outputs = getWrapper().execute(inputFile, inputFile2, output1, output2, extraArgs);
        if (!SequencePipelineService.get().hasMinLineCount(outputs.first, 4))
        {
            getPipelineCtx().getJob().setStatus(PipelineJob.TaskStatus.error, "No passing reads were found");
            throw new PipelineJobException("No passing reads were found: " + inputFile.getPath());
        }

        output.setProcessedFastq(Pair.of(output1, output2));
        output.addOutput(summary, "PrintReadsContaining Summary File");

        return output;
    }
    
    public static class Wrapper extends DISCVRSeqRunner
    {
        public Wrapper(Logger log)
        {
            super(log);
        }

        public Pair<File, File> execute(File inputFile1, @Nullable File inputFile2, File output1, @Nullable File output2, List<String> extraArgs) throws PipelineJobException
        {
            List<String> args = getBaseArgs("PrintReadsContaining");
            args.add("--fastq");
            args.add(inputFile1.getPath());

            args.add("--output");
            args.add(output1.getPath());

            if (inputFile2 != null)
            {
                args.add("--fastq2");
                args.add(inputFile2.getPath());

                args.add("--output2");
                args.add(output2.getPath());
            }

            args.addAll(extraArgs);

            execute(args);

            if (!output1.exists())
            {
                throw new PipelineJobException("Unable to find file: " + output1.getPath());
            }

            if (output2 != null && !output2.exists())
            {
                throw new PipelineJobException("Unable to find file: " + output2.getPath());
            }

            return Pair.of(output1, output2);
        }
    }
}
