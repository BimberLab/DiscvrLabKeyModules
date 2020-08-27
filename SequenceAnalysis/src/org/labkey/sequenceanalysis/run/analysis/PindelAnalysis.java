package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SamPairUtil;
import htsjdk.samtools.metrics.MetricsFile;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.writer.PrintWriters;
import picard.analysis.InsertSizeMetrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class PindelAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public PindelAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<LofreqAnalysis>
    {
        public Provider()
        {
            super("pindel", "Pindel Analysis", null, "This will run pindel on BAMs created as part of LowFreq, a tool designed to call low-frequency mutations in a sample, such as viral populations or bacteria.  It is recommended to run GATK's BQSR and IndelRealigner upstream of this tool.", Arrays.asList(
                    ToolParameterDescriptor.create("minFraction", "Min Fraction To Report", "Only variants representing at least this fraction of reads (based on depth at the start position) will be reported.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0.0);
                        put("maxValue", 1.0);
                        put("decimalPrecision", 2);
                    }}, 0.1),
                    ToolParameterDescriptor.create("minDepth", "Min Depth To Report", "Only variants representing at least this many reads (based on depth at the start position) will be reported.", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 10),
                    ToolParameterDescriptor.create("writeToBamDir", "Write To BAM Dir", "If checked, outputs will be written to the BAM folder, as opposed to the output folder for this job.", "checkbox", new JSONObject(){{

                    }}, false),
                    ToolParameterDescriptor.create("removeDuplicates", "Remove Duplicates", "If checked, a temporatory BAM will be treated with reads marked as duplicates dropped.", "checkbox", new JSONObject(){{

                    }}, true)
            ), null, null);
        }


        @Override
        public PindelAnalysis create(PipelineContext ctx)
        {
            return new PindelAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        boolean writeToBamDir = getProvider().getParameterByName("writeToBamDir").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        boolean removeDuplicates = getProvider().getParameterByName("removeDuplicates").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        Double minFraction = getProvider().getParameterByName("minFraction").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Double.class, 0.0);
        int minDepth = getProvider().getParameterByName("minDepth").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 0);

        File out = writeToBamDir ? inputBam.getParentFile() : outputDir;
        File summary = runPindel(output, getPipelineCtx(), rs, out, inputBam, referenceGenome.getWorkingFastaFile(), minFraction, minDepth, removeDuplicates);
        long lineCount = SequencePipelineService.get().getLineCount(summary) - 1;
        if (lineCount > 0)
        {
            output.addSequenceOutput(summary, rs.getName() + ": pindel", "Pindel Variants", rs.getReadsetId(), null, referenceGenome.getGenomeId(), "Total variants: " + (lineCount - 1));
        }
        else
        {
            getPipelineCtx().getLogger().info("No passing variants found");
        }

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    private static String inferInsertSize(PipelineContext ctx, File bam) throws IOException
    {
        File expectedPicard = new File(bam.getParentFile(), FileUtil.getBaseName(bam.getName()) + ".insertsize.metrics");
        if (expectedPicard.exists())
        {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(expectedPicard), StringUtilsLabKey.DEFAULT_CHARSET)))
            {
                MetricsFile metricsFile = new MetricsFile();
                metricsFile.read(reader);
                List<InsertSizeMetrics> metrics = metricsFile.getMetrics();
                for (InsertSizeMetrics m : metrics)
                {
                    if (m.PAIR_ORIENTATION == SamPairUtil.PairOrientation.FR)
                    {
                        return String.valueOf(Math.ceil(m.MEAN_INSERT_SIZE));
                    }
                }
            }
        }

        ctx.getLogger().debug("Unable to parse insert size, defaulting to 250");

        return "250";
    }

    public static File runPindel(AnalysisOutputImpl output, PipelineContext ctx, Readset rs, File outDir, File bam, File fasta, double minFraction, int minDepth, boolean removeDuplicates) throws PipelineJobException
    {
        File bamToUse = removeDuplicates ? new File(outDir, FileUtil.getBaseName(bam) + ".rmdup.bam") : bam;
        if (removeDuplicates)
        {
            File bamIdx = new File(bamToUse.getPath() + ".bai");
            if (!bamIdx.exists())
            {
                SamtoolsRunner runner = new SamtoolsRunner(ctx.getLogger());
                runner.execute(Arrays.asList(runner.getSamtoolsPath().getPath(), "rmdup", bam.getPath(), bamToUse.getPath()));
                runner.execute(Arrays.asList(runner.getSamtoolsPath().getPath(), "index", bamToUse.getPath()));
            }
            else
            {
                ctx.getLogger().debug("rmdup BAM already exists, reusing");
            }

            output.addIntermediateFile(bamToUse);
            output.addIntermediateFile(bamIdx);
        }

        File pindelParams = new File(outDir, "pindelCfg.txt");
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(pindelParams), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            String insertSize = inferInsertSize(ctx, outDir);
            writer.writeNext(new String[]{bam.getPath(), insertSize, FileUtil.makeLegalName(rs.getName())});
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        SimpleScriptWrapper wrapper = new SimpleScriptWrapper(ctx.getLogger());

        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getExeForPackage("PINDELPATH", "pindel").getPath());
        args.add("-f");
        args.add(fasta.getPath());
        args.add("-i");
        args.add(pindelParams.getPath());
        args.add("-o");
        File outPrefix = new File(outDir, FileUtil.getBaseName(bam) + ".pindel");
        args.add(outPrefix.getPath());

        Integer threads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
        if (threads != null)
        {
            args.add("-T");
            args.add(threads.toString());
        }

        wrapper.execute(args);

        File outTsv = new File(outDir, FileUtil.getBaseName(bam) + ".pindel.txt");
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outTsv), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            writer.writeNext(new String[]{"Type", "Contig", "Start", "End", "Depth", "ReadSupport", "Fraction"});
            parsePindelOutput(ctx, writer, new File(outPrefix.getPath() + "_D"), bam, minFraction, minDepth);
            parsePindelOutput(ctx, writer, new File(outPrefix.getPath() + "_INV"), bam, minFraction, minDepth);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return outTsv;
    }

    private static void parsePindelOutput(PipelineContext ctx, CSVWriter writer, File pindelFile, File bam, double minFraction, int minDepth) throws IOException
    {
        try (BufferedReader reader = Readers.getReader(pindelFile))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.contains("Supports "))
                {
                    String[] tokens = line.split("\t");
                    int support = Integer.parseInt(tokens[8].split(" ")[1]);
                    if (support < minDepth)
                    {
                        continue;
                    }

                    String contig = tokens[3].split(" ")[1];
                    int start = Integer.parseInt(tokens[4].split(" ")[1]);

                    int depth = getGatkDepth(ctx, bam, contig, start);
                    if (depth == 0)
                    {
                        continue;
                    }

                    double pct = (double)support / depth;
                    if (pct >= minFraction)
                    {
                        int end = Integer.parseInt(tokens[5]);
                        String type = tokens[1].split(" ")[0];
                        writer.writeNext(new String[]{type, contig, String.valueOf(start), String.valueOf(end), String.valueOf(depth), String.valueOf(support), String.valueOf(pct)});
                    }
                }
            }
        }
    }

    private static int getGatkDepth(PipelineContext ctx, File bam, String contig, int position1) throws IOException
    {
        File gatkDepth = new File(bam.getParentFile(), FileUtil.getBaseName(bam) + ".lofreq.coverage");

        //skip header:
        int lineNo = 1 + position1;
        try (Stream<String> lines = Files.lines(gatkDepth.toPath()))
        {
            String[] line = lines.skip(lineNo - 1).findFirst().get().split("\t");

            if (!line[0].equals(contig + ":" + position1))
            {
                throw new IOException("Incorrect line at " + lineNo + ", expected " + contig + ":" + position1 + ", but was: " + line[0]);
            }

            return Integer.parseInt(line[1]);
        }
        catch (Exception e)
        {
            ctx.getLogger().error("Error parsing GATK depth: " + gatkDepth.getPath() + " / " + lineNo);
            throw new IOException(e);
        }
    }
}