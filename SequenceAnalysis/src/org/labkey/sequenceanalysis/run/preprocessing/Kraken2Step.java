package org.labkey.sequenceanalysis.run.preprocessing;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
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
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Kraken2Step extends AbstractCommandPipelineStep<Kraken2Step.Kraken2Wrapper> implements PreprocessingStep
{
    private static final String DB_PARAM = "db";
    private static final String MODE_PARAM = "mode";

    public Kraken2Step(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new Kraken2Wrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<PreprocessingStep>
    {
        public Provider()
        {
            super("Kraken2", "Kraken2", "Kraken2", "This step aligns input reads against a reference using BWA-mem and will only return read pairs without a passing hit in either read.", Arrays.asList(
                ToolParameterDescriptor.create(DB_PARAM, "Database", "This determines the DB for positive or negative selection", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", "bacteria-viral");
                    put("multiSelect", false);
                    put("allowBlank", false);
                    put("joinReturnValue", true);
                    put("delimiter", ";");
                }}, "bacteria-viral"),
                ToolParameterDescriptor.create(MODE_PARAM, "Reads To Retain", "This determines which set of reads is passed to the next step. If 'Retain Classified' is selected, then reads matching the DB are retained. if 'Retain Unclassified' is selected, then reads that do not match the DB are retained", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", "Classified;Unclassified");
                    put("multiSelect", false);
                    put("allowBlank", false);
                    put("joinReturnValue", true);
                    put("delimiter", ";");
                }}, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--minimum-hit-groups"), "minimumHitGroups", "Minimum Hit Groups", "Minimum number of hit groups (overlapping k-mers sharing the same minimizer) needed to make a call", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 0);
                }}, 2),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--confidence"), "confidence", "Confidence", "Confidence score threshold (0-1)", "ldk-numberfield", new JSONObject(){{
                    put("minValue", 0);
                    put("maxValue", 1);
                    put("decimalPrecision", 2);
                }}, 0)
            ), null, "https://github.com/DerrickWood/kraken2");
        }

        @Override
        public Kraken2Step create(PipelineContext context)
        {
            return new Kraken2Step(this, context);
        }
    }

    @Override
    public Output processInputFile(File inputFile, @Nullable File inputFile2, File outputDir) throws PipelineJobException
    {
        PreprocessingOutputImpl output = new PreprocessingOutputImpl(inputFile, inputFile2);

        List<String> args = new ArrayList<>();
        args.add(getWrapper().getExe().getPath());

        if (inputFile2 != null)
        {
            args.add("--paired");
        }

        if (inputFile.getName().toLowerCase().endsWith(".gz"))
        {
            args.add("--gzip-compressed");
        }

        Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (threads != null)
        {
            args.add("--threads");
            args.add(threads.toString());
        }

        String dbName = getProvider().getParameterByName(DB_PARAM).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        if (dbName == null)
        {
            throw new PipelineJobException("Missing DB name");
        }

        File binDir = FileUtil.appendName(new File(PipelineJobService.get().getAppProperties().getToolsDirectory()), "kraken2_dbs");
        if (!binDir.exists())
        {
            throw new PipelineJobException("Unable to find kraken2 DB dir, expected: " + binDir.getAbsolutePath());
        }

        File dbDir = FileUtil.appendName(binDir, dbName);
        if (!dbDir.exists())
        {
            throw new PipelineJobException("Unable to find kraken2 DB dir, expected: " + dbDir.getAbsolutePath());
        }

        args.add("--use-names");

        args.add("--db");
        args.add(dbDir.getAbsolutePath());

        args.addAll(getClientCommandArgs());

        args.add("--output");
        args.add("-");

        String mode = getProvider().getParameterByName(MODE_PARAM).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);

        File classifiedOutputBase = FileUtil.appendName(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputFile.getName()) + ".classified");
        File unclassifiedOutputBase = FileUtil.appendName(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputFile.getName()) + ".unclassified");
        if ("Classified".equals(mode))
        {
            args.add("--classified-out");
            args.add(classifiedOutputBase.getPath() + "#.fq");
        }
        else
        {
            args.add("--unclassified-out");
            args.add(unclassifiedOutputBase.getPath() + "#.fq");
        }

        File reportFile = FileUtil.appendName(outputDir, SequencePipelineService.get().getUnzippedBaseName(inputFile.getName()) + ".kraken2.report.txt");
        args.add("--report");
        args.add(reportFile.getPath());

        args.add(inputFile.getPath());
        if (inputFile2 != null)
        {
            args.add(inputFile2.getPath());
        }

        getWrapper().execute(args);

        if ("Classified".equals(mode))
        {
            File classified1 = new File(classifiedOutputBase.getPath() + "_1.fq");
            File classified2 = inputFile2 == null ? null : new File(classifiedOutputBase.getPath() + "_2.fq");
            if (!classified1.exists())
            {
                throw new PipelineJobException("Classified file does not exist: " +  classified1.getAbsolutePath());
            }

            File compressed1 = Compress.compressGzip(classified1);
            output.addIntermediateFile(classified1);

            File compressed2 = classified2 == null ? null : Compress.compressGzip(classified2);
            if (classified2 != null)
            {
                output.addIntermediateFile(classified2);
            }

            output.setProcessedFastq(Pair.of(compressed1, compressed2));
        }
        else
        {
            File unclassified1 = new File(unclassifiedOutputBase.getPath() + "_1.fq");
            File unclassified2 = inputFile2 == null ? null : new File(unclassifiedOutputBase.getPath() + "_2.fq");
            if (!unclassified1.exists())
            {
                throw new PipelineJobException("Unclassified file does not exist: " +  unclassified1.getAbsolutePath());
            }

            File compressed1 = Compress.compressGzip(unclassified1);
            output.addIntermediateFile(unclassified1);

            File compressed2 = unclassified2 == null ? null : Compress.compressGzip(unclassified2);
            if (unclassified2 != null)
            {
                output.addIntermediateFile(unclassified2);
            }

            output.setProcessedFastq(Pair.of(compressed1, compressed2));
        }

        return output;
    }

    public static class Kraken2Wrapper extends AbstractCommandWrapper
    {
        public Kraken2Wrapper(Logger log)
        {
            super(log);
        }

        public File getExe()
        {
            return SimpleScriptWrapper.resolveFileInPath("kraken2", null, true);
        }
    }
}
