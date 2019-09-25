package org.labkey.sequenceanalysis.run.analysis;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubreadAnalysis extends AbstractCommandPipelineStep<SubreadAnalysis.SubreadWrapper> implements AnalysisStep
{
    public SubreadAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new SubreadWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<SubreadAnalysis>
    {
        public Provider()
        {
            super("Subread", "Subread Feature Counts", null, "This generate a table of feature counts from a given BAM file.", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam("gtf", "Gene File", "This is the ID of a GTF or GFF3 file containing genes from this genome.  It will be used to identify splice sites.  If a GFF3 file is selected, you must also provide the ID used to specify parent features.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extensions", Arrays.asList("gtf", "gff"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-M"), "countMultiMappingReads", "Count Multi Mapping Reads", "If checked, reads mapped to more than one locus will be counted", "checkbox", new JSONObject(){{

                    }}, false),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-O"), "allowMultiOverlap", "Allow Multi Overlap", "If checked, reads aligning to more than one meta feature (typically transcript) will be counted.  Note: this means that read will be counted more than once.", "checkbox", new JSONObject(){{

                    }}, false),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-p"), "isPairedEnd", "Count Fragments", "If specified, fragments (or templates) will be counted instead of reads. This option is only applicable for paired-end reads.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--fraction"), "fraction", "Assigned Fractional Counts", "If specified, multi-mapping or overlapping reads will be assigned a fractional count.  Must be used with either -M or -O.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--primary"), "primary", "Primary Alignments Only", "If specified, only primary alignments will be counted.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--ignoreDup"), "ignoreDup", "Ignore Duplicates", "If specified, reads flagged as duplicated will be ignored.", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    ToolParameterDescriptor.create("strandSpecific", "Strand Specific", "If reads are stranded, specify that here.", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "Unstranded;Stranded;Reversely Stranded");
                        put("value", "Unstranded");
                    }}, "Unstranded")
            ), PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js"), "http://bioinf.wehi.edu.au/featureCounts/");
        }

        @Override
        public SubreadAnalysis create(PipelineContext ctx)
        {
            return new SubreadAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();

        args.addAll(getClientCommandArgs());

        if (args.contains("--fraction") && !(args.contains("-O") || args.contains("-M")))
        {
            getPipelineCtx().getLogger().warn("--fraction argument supplied without -O or -M, ignoring");
            args.remove("--fraction");
        }

        if (args.contains("-M") && args.contains("--primary"))
        {
            getPipelineCtx().getLogger().warn("--primary argument supplied along with -M, ignoring --primary");
            args.remove("--primary");
        }

        String strandSpecific = getProvider().getParameterByName("strandSpecific").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        if (strandSpecific != null)
        {
            args.add("-s");
            switch (strandSpecific)
            {
                case "Stranded":
                    args.add("1");
                    break;
                case "Reversely Stranded":
                    args.add("2");
                    break;
                case "Unstranded":
                default:
                    args.add("0");
            }
        }

        File gtf = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("gtf").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
        args.add("-a");
        args.add(gtf.getPath());

        File outputFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".featureCounts.txt");

        Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (threads != null)
        {
            args.add("-T");
            args.add(threads.toString());
        }

        new SubreadWrapper(getPipelineCtx().getLogger()).execute(inputBam, outputFile, args);

        if (!outputFile.exists())
        {
            throw new PipelineJobException("Unable to find output: " + outputFile.getPath());
        }

        AnalysisOutputImpl output = new AnalysisOutputImpl();

        output.addInput(inputBam, "BAM File");
        output.addSequenceOutput(outputFile, "Feature Counts: " + inputBam.getName(), "Subread Feature Counts", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);

        File summary = new File(outputFile.getPath() + ".summary");
        if (summary.exists())
        {
            getPipelineCtx().getLogger().info("count summary");
            try (BufferedReader reader = Readers.getReader(summary))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    getPipelineCtx().getLogger().info(line);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    public static class SubreadWrapper extends AbstractCommandWrapper
    {
        public SubreadWrapper(Logger log)
        {
            super(log);
        }

        public void execute(File inputBam, File outputFile, List<String> extraArgs) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());

            args.add("-o");
            args.add(outputFile.getPath());

            args.addAll(extraArgs);

            args.add(inputBam.getPath());

            execute(args);
        }

        protected File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("SUBREADPATH", "featureCounts");
        }
    }
}