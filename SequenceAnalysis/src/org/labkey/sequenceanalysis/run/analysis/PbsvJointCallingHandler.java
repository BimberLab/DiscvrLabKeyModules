package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
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
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME), "Pbsv Call", "Runs pbsv call, which jointly calls genotypes from PacBio data", new LinkedHashSet<>(List.of("sequenceanalysis/panel/VariantScatterGatherPanel.js")), Arrays.asList(
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
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--ccs"), "useCCS", "Use CCS", "If checked, the --ccs option is added to pbsv call.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("doSplitJobs", "Split Jobs", "If checked, this will submit one job per input, instead of one merged job.  This can be useful for processing large batches", "checkbox", null, false),
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

            if (getVariantPipelineJob(ctx.getJob()) != null && getVariantPipelineJob(ctx.getJob()).isScatterJob())
            {
                outputBaseName = outputBaseName + "." + getVariantPipelineJob(ctx.getJob()).getIntervalSetName();
            }

            File expectedFinalOutput = new File(ctx.getOutputDir(), outputBaseName + ".vcf.gz");
            File expectedFinalOutputIdx = new File(expectedFinalOutput.getPath() + ".tbi");
            boolean jobCompleted = expectedFinalOutputIdx.exists();  // this would occur if the job died during the cleanup phase

            List<File> outputs = new ArrayList<>();
            if (getVariantPipelineJob(ctx.getJob()) != null && getVariantPipelineJob(ctx.getJob()).isScatterJob())
            {
                for (Interval i : getVariantPipelineJob(ctx.getJob()).getIntervalsForTask())
                {
                    if (i.getStart() != 1)
                    {
                        throw new PipelineJobException("Expected all intervals to start on the first base: " + i);
                    }

                    File o = runPbsvCall(ctx, filesToProcess, genome, outputBaseName + (getVariantPipelineJob(ctx.getJob()).getIntervalsForTask().size() == 1 ? "" : "." + i.getContig()), i.getContig(), jobCompleted);
                    if (o != null)
                    {
                        outputs.add(o);
                    }
                }
            }
            else
            {
                outputs.add(runPbsvCall(ctx, filesToProcess, genome, outputBaseName, null, jobCompleted));
            }

            try
            {
                File vcfOutGz;
                if (outputs.size() == 1)
                {
                    if (jobCompleted)
                    {
                        ctx.getLogger().debug("The final output VCF and index are found, so this is likely a resumed job. skipping merge");
                        vcfOutGz = expectedFinalOutput;
                    }
                    else
                    {
                        File unzipVcfOut = outputs.get(0);
                        vcfOutGz = SequenceAnalysisService.get().bgzipFile(unzipVcfOut, ctx.getLogger());
                        if (unzipVcfOut.exists())
                        {
                            throw new PipelineJobException("Unzipped VCF should not exist: " + vcfOutGz.getPath());
                        }
                    }
                }
                else
                {
                    for (File f : outputs)
                    {
                        ctx.getFileManager().addIntermediateFile(f);
                        ctx.getFileManager().addIntermediateFile(SequenceAnalysisService.get().ensureVcfIndex(f, ctx.getLogger(), false));
                    }

                    if (jobCompleted)
                    {
                        ctx.getLogger().debug("The final output VCF and index are found, so this is likely a resumed job. skipping merge");
                        vcfOutGz = expectedFinalOutput;
                    }
                    else
                    {
                        vcfOutGz = SequenceUtil.combineVcfs(outputs, genome, expectedFinalOutput, ctx.getLogger(), true, null, false, true);

                        // NOTE: the resulting file can be out of order due to translocations
                        SequenceUtil.sortROD(vcfOutGz, ctx.getLogger(), 2);
                    }
                }

                SequenceAnalysisService.get().ensureVcfIndex(vcfOutGz, ctx.getLogger(), true);

                SequenceOutputFile so = new SequenceOutputFile();
                so.setName("pbsv call: " + outputBaseName);
                so.setFile(vcfOutGz);
                so.setCategory(OUTPUT_CATEGORY);
                so.setLibrary_id(genome.getGenomeId());

                ctx.addSequenceOutput(so);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private File runPbsvCall(JobContext ctx, List<File> inputs, ReferenceGenome genome, String outputBaseName, @Nullable String contig, boolean jobCompleted) throws PipelineJobException
        {
            if (contig != null)
            {
                ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Processing: " + contig);
            }

            if (inputs.isEmpty())
            {
                throw new PipelineJobException("No inputs provided");
            }

            File vcfOut = new File(ctx.getOutputDir(), outputBaseName + ".vcf");
            File doneFile = new File(ctx.getOutputDir(), outputBaseName + ".done");
            ctx.getFileManager().addIntermediateFile(doneFile);
            if (doneFile.exists())
            {
                ctx.getLogger().info("Existing file, found, re-using");
                verifyAndAddMissingSamples(ctx, vcfOut, inputs, genome);
                return vcfOut;
            }
            else if (jobCompleted)
            {
                ctx.getLogger().debug("The overall job has completed and this is a job resume. Skipping pbsv call");
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
                    String ret = StringUtils.trimToNull(runner.executeWithOutput(Arrays.asList("/bin/bash", "-c", tabix.getExe().getPath() + " -l '" + s.getPath() + "' | awk ' $1 == \"" + contig + "\" ' | wc -l")));
                    if ("0".equals(ret))
                    {
                        ctx.getLogger().info("Sample is missing contig: " + contig + ", skipping: " + s.getPath());
                    }
                    else if ("1".equals(ret))
                    {
                        samplesToUse.add(s);
                    }
                    else
                    {
                        throw new PipelineJobException("Unknown output: " + ret);
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

            verifyAndAddMissingSamples(ctx, vcfOut, inputs, genome);

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
        if (job instanceof VariantProcessingJob vpj)
        {

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

    public void verifyAndAddMissingSamples(JobContext ctx, File input, List<File> inputFiles, ReferenceGenome genome) throws PipelineJobException
    {
        ctx.getLogger().debug("Verifying sample list in output VCF");

        List<String> sampleNamesInOrder = new ArrayList<>();
        inputFiles.forEach(f -> {
            sampleNamesInOrder.add(SequenceAnalysisService.get().getUnzippedBaseName(f.getName()));
        });

        try
        {
            SequenceAnalysisService.get().ensureVcfIndex(input, ctx.getLogger(), false);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        File output = new File(input.getPath() + ".tmp.vcf");
        try (VCFFileReader reader = new VCFFileReader(input))
        {
            VCFHeader header = reader.getHeader();
            List<String> existingSamples = header.getSampleNamesInOrder();
            if (existingSamples.equals(sampleNamesInOrder))
            {
                ctx.getLogger().debug("Samples are identical, no need to update VCF");
                return;
            }

            ctx.getLogger().debug("Will add missing samples. Total pre-existing: " + existingSamples.size() + " of " + sampleNamesInOrder.size());
            SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
            try (VariantContextWriter writer = new VariantContextWriterBuilder().setOutputFile(output).setReferenceDictionary(dict).build();CloseableIterator<VariantContext> it = reader.iterator())
            {
                header = new VCFHeader(header.getMetaDataInInputOrder(), sampleNamesInOrder);
                header.setSequenceDictionary(dict);
                writer.writeHeader(header);

                while (it.hasNext())
                {
                    VariantContext vc = it.next();
                    VariantContextBuilder vcb = new VariantContextBuilder(vc);
                    List<Genotype> genotypes = new ArrayList<>();
                    for (String sample : sampleNamesInOrder)
                    {
                        if (vc.hasGenotype(sample))
                        {
                            genotypes.add(vc.getGenotype(sample));
                        }
                        else
                        {
                            genotypes.add(new GenotypeBuilder(sample, Arrays.asList(Allele.NO_CALL, Allele.NO_CALL)).make());
                        }
                    }

                    vcb.genotypes(genotypes);

                    writer.add(vcb.make());
                }
            }
        }

        try
        {
            // Replace input
            input.delete();
            FileUtils.moveFile(output, input);

            // And index
            File idx = new File(input.getPath() + ".idx");
            if (idx.exists())
            {
                idx.delete();
            }

            File outputIdx = new File(output.getPath() + ".idx");
            if (outputIdx.exists())
            {
                FileUtils.moveFile(outputIdx, idx);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    public boolean doSortAfterMerge()
    {
        return true;
    }
}
