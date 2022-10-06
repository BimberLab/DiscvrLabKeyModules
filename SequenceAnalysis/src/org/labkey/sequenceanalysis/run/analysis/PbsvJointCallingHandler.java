package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.util.Interval;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;
import org.labkey.sequenceanalysis.run.util.AbstractGenomicsDBImportHandler;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

public class PbsvJointCallingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> implements SequenceOutputHandler.TracksVCF, VariantProcessingStep.SupportsScatterGather
{
    private static final FileType FILE_TYPE = new FileType(".svsig.gz");
    private static final String OUTPUT_CATEGORY = "PBSV VCF";

    public PbsvJointCallingHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME), "Pbsv Call", "Runs pbsv call, which jointly calls genotypes from PacBio data", new LinkedHashSet<>(Arrays.asList("sequenceanalysis/panel/VariantScatterGatherPanel.js")), Arrays.asList(
                ToolParameterDescriptor.create("fileName", "VCF Filename", "The name of the resulting file.", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                    put("doNotIncludeInTemplates", true);
                }}, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--log-level"), "logLevel", "Log Level", "Controls logging", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", "DEBUG;INFO;WARN");
                    put("multiSelect", false);
                }}, "INFO"),
                ToolParameterDescriptor.create("doCopyLocal", "Copy Inputs Locally", "If checked, the input file(s) will be copied to the job working directory.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create("scatterGather", "Scatter/Gather Options", "If selected, this job will be divided to run job per chromosome.  The final step will take the VCF from each intermediate step and combined to make a final VCF file.", "sequenceanalysis-variantscattergatherpanel", null, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && FILE_TYPE.isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            List<File> inputs = inputFiles.stream().map(SequenceOutputFile::getFile).collect(Collectors.toList());
            if (ctx.getParams().optBoolean("doCopyLocal", false))
            {
                ctx.getLogger().info("Copying inputs locally");
                try
                {
                    List<File> copiedInputs = new ArrayList<>();
                    for (File f : inputs)
                    {
                        File copied = new File(ctx.getWorkingDirectory(), f.getName());
                        if (copiedInputs.contains(copied))
                        {
                            throw new PipelineJobException("Duplicate input filenames, cannot use with copyLocally option: " + copied.getName());
                        }

                        if (copied.exists())
                        {
                            copied.delete();
                        }

                        FileUtils.copyFile(f, copied);
                        copiedInputs.add(copied);

                        ctx.getFileManager().addIntermediateFile(copied);
                    }

                    inputs = copiedInputs;
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenomes().iterator().next();
            String outputBaseName = ctx.getParams().getString("fileName");
            if (!outputBaseName.toLowerCase().endsWith(".gz"))
            {
                outputBaseName = outputBaseName.replaceAll(".gz$", "");
            }

            if (!outputBaseName.toLowerCase().endsWith(".vcf"))
            {
                outputBaseName = outputBaseName.replaceAll(".vcf$", "");
            }

            List<File> outputs = new ArrayList<>();
            if (getVariantPipelineJob(ctx.getJob()).isScatterJob())
            {
                outputBaseName = outputBaseName + "." + getVariantPipelineJob(ctx.getJob()).getIntervalSetName();
                for (Interval i : getVariantPipelineJob(ctx.getJob()).getIntervalsForTask())
                {
                    if (i.getStart() != 1)
                    {
                        throw new PipelineJobException("Expected all intervals to start on the first base: " + i.toString());
                    }

                    outputs.add(runPbsvCall(ctx, inputs, genome, outputBaseName + (getVariantPipelineJob(ctx.getJob()).getIntervalsForTask().size() == 1 ? "" : "." + i.getContig()), i.getContig()));
                }
            }
            else
            {
                outputs.add(runPbsvCall(ctx, inputs, genome, outputBaseName, null));
            }

            File vcfOutGz;
            if (outputs.size() == 1)
            {
                File unzipVcfOut = outputs.get(0);
                vcfOutGz = SequenceAnalysisService.get().bgzipFile(unzipVcfOut, ctx.getLogger());
            }
            else
            {
                vcfOutGz = SequenceUtil.combineVcfs(outputs, genome, new File(ctx.getOutputDir(), outputBaseName), ctx.getLogger(), true, null, false);
                if (vcfOutGz.exists())
                {
                    throw new PipelineJobException("Unzipped VCF should not exist: " + vcfOutGz.getPath());
                }
            }

            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(vcfOutGz, ctx.getLogger(), true);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            SequenceOutputFile so = new SequenceOutputFile();
            so.setName("pbsv call: " + outputBaseName);
            so.setFile(vcfOutGz);
            so.setCategory(OUTPUT_CATEGORY);
            so.setLibrary_id(genome.getGenomeId());

            ctx.addSequenceOutput(so);
        }

        private File runPbsvCall(JobContext ctx, List<File> inputs, ReferenceGenome genome, String outputBaseName, @Nullable String contig) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());
            args.add("call");

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            if (maxThreads != null)
            {
                args.add("-j");
                args.add(String.valueOf(maxThreads));
            }

            if (contig != null)
            {
                args.add("-r");
                args.add(contig);
            }

            args.addAll(getClientCommandArgs(ctx.getParams()));

            args.add(genome.getWorkingFastaFile().getPath());

            inputs.forEach(f -> {
                args.add(f.getPath());
            });

            File vcfOut = new File(ctx.getOutputDir(), outputBaseName + ".vcf");
            args.add(vcfOut.getPath());

            new SimpleScriptWrapper(ctx.getLogger()).execute(args);

            if (!vcfOut.exists())
            {
                throw new PipelineJobException("Unable to find file: " + vcfOut.getPath());
            }

            return vcfOut;
        }

        private File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("PBSVPATH", "pbsv");
        }
    }

    @Override
    public File getScatterJobOutput(JobContext ctx) throws PipelineJobException
    {
        return ProcessVariantsHandler.getScatterOutputByCategory(ctx, OUTPUT_CATEGORY);
    }

    @Override
    public void validateScatter(VariantProcessingStep.ScatterGatherMethod method, PipelineJob job) throws IllegalArgumentException
    {
        AbstractGenomicsDBImportHandler.validateNoSplitContigScatter(method, job);
    }

    @Override
    public SequenceOutputFile createFinalSequenceOutput(PipelineJob job, File processed, List<SequenceOutputFile> inputFiles)
    {
        return ProcessVariantsHandler.createSequenceOutput(job, processed, inputFiles, OUTPUT_CATEGORY);
    }

    private static VariantProcessingJob getVariantPipelineJob(PipelineJob job)
    {
        return job instanceof VariantProcessingJob ? (VariantProcessingJob)job : null;
    }
}
