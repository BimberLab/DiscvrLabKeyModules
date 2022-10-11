package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.util.Interval;
import org.apache.commons.io.FileUtils;
import org.json.old.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
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
import org.labkey.sequenceanalysis.ScatterGatherUtils;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;
import org.labkey.sequenceanalysis.run.util.AbstractGenomicsDBImportHandler;
import org.labkey.sequenceanalysis.run.util.GenotypeGVCFsWrapper;
import org.labkey.sequenceanalysis.run.util.TabixRunner;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PbsvJointCallingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> implements SequenceOutputHandler.TracksVCF, VariantProcessingStep.SupportsScatterGather, VariantProcessingStep.MayRequirePrepareTask
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
            Set<File> toDelete = new HashSet<>();
            List<File> filesToProcess = new ArrayList<>();
            if (doCopyLocal(ctx.getParams()))
            {
                ctx.getLogger().info("making local copies of svsig files");
                filesToProcess.addAll(GenotypeGVCFsWrapper.copyVcfsLocally(ctx, inputs, toDelete, false));
            }
            else
            {
                filesToProcess.addAll(inputs);
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

                    File o = runPbsvCall(ctx, filesToProcess, genome, outputBaseName + (getVariantPipelineJob(ctx.getJob()).getIntervalsForTask().size() == 1 ? "" : "." + i.getContig()), i.getContig());
                    if (o != null)
                    {
                        outputs.add(o);
                    }
                }
            }
            else
            {
                outputs.add(runPbsvCall(ctx, filesToProcess, genome, outputBaseName, null));
            }

            File vcfOutGz;
            if (outputs.size() == 1)
            {
                File unzipVcfOut = outputs.get(0);
                vcfOutGz = SequenceAnalysisService.get().bgzipFile(unzipVcfOut, ctx.getLogger());
                if (unzipVcfOut.exists())
                {
                    throw new PipelineJobException("Unzipped VCF should not exist: " + vcfOutGz.getPath());
                }
            }
            else
            {
                outputs.forEach(f -> ctx.getFileManager().addIntermediateFile(f));
                vcfOutGz = SequenceUtil.combineVcfs(outputs, genome, new File(ctx.getOutputDir(), outputBaseName), ctx.getLogger(), true, null, false);
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
            File vcfOut = new File(ctx.getOutputDir(), outputBaseName + ".vcf");
            File doneFile = new File(ctx.getOutputDir(), outputBaseName + ".done");
            ctx.getFileManager().addIntermediateFile(doneFile);
            if (doneFile.exists())
            {
                ctx.getLogger().info("Existing file, found, re-using");
                return vcfOut;
            }

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

            // Check indexes to determine whether each sample actually includes this contig. This is mostly relevant for small contigs:
            List<File> samplesToUse = new ArrayList<>();
            if (contig != null)
            {
                ctx.getLogger().info("Checking each input for usage of contig: " + contig);
                SimpleScriptWrapper runner = new SimpleScriptWrapper(ctx.getLogger());
                TabixRunner tabix = new TabixRunner(ctx.getLogger());

                for (File s : inputs)
                {
                    String ret = StringUtils.trimToNull(runner.executeWithOutput(Arrays.asList("/bin/bash", "-c", tabix.getExe().getPath() + " -l '" + s.getPath() + "' | grep -e '" + contig + "' | wc -l")));
                    if ("0".equals(ret))
                    {
                        ctx.getLogger().info("Sample is missing contig: " + contig + ", skipping: " + s.getPath());
                    }
                    else
                    {
                        samplesToUse.add(s);
                    }
                }
            }
            else
            {
                samplesToUse = inputs;
            }

            if (samplesToUse.isEmpty())
            {
                ctx.getLogger().info("No samples had data for contig: " + contig + ", skipping");
                return null;
            }

            ctx.getLogger().info("Using " + samplesToUse.size() + " of " + inputs.size() + " svsig files");
            samplesToUse.forEach(f -> {
                args.add(f.getPath());
            });

            args.add(vcfOut.getPath());

            new SimpleScriptWrapper(ctx.getLogger()).execute(args);

            if (!vcfOut.exists())
            {
                throw new PipelineJobException("Unable to find file: " + vcfOut.getPath());
            }

            try
            {
                FileUtils.touch(doneFile);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
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

    @Override
    public boolean isRequired(PipelineJob job)
    {
        if (job instanceof VariantProcessingJob)
        {
            VariantProcessingJob vpj = (VariantProcessingJob)job;

            return doCopyLocal(vpj.getParameterJson());
        }

        return false;
    }

    private boolean doCopyLocal(JSONObject params)
    {
        return params.optBoolean("doCopyLocal", false);
    }

    @Override
    public void doWork(List<SequenceOutputFile> inputFiles, JobContext ctx) throws PipelineJobException
    {
        ScatterGatherUtils.doCopyGvcfLocally(inputFiles, ctx);
    }
}
