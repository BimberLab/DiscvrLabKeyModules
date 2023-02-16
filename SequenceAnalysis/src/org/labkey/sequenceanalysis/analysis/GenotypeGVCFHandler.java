package org.labkey.sequenceanalysis.analysis;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.run.DISCVRSeqRunner;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.ScatterGatherUtils;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.JobContextImpl;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;
import org.labkey.sequenceanalysis.run.util.AbstractGenomicsDBImportHandler;
import org.labkey.sequenceanalysis.run.util.GenomicsDBImportHandler;
import org.labkey.sequenceanalysis.run.util.GenomicsDbImportWrapper;
import org.labkey.sequenceanalysis.run.util.GenotypeGVCFsWrapper;
import org.labkey.sequenceanalysis.run.util.MergeVcfsAndGenotypesWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler.VCF_CATEGORY;
import static org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler.getIntervals;

/**
 * Created by bimber on 8/26/2014.
 */
public class GenotypeGVCFHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>, SequenceOutputHandler.HasActionNames, SequenceOutputHandler.TracksVCF, VariantProcessingStep.MayRequirePrepareTask, VariantProcessingStep.SupportsScatterGather
{
    private final FileType _gvcfFileType = new FileType(List.of(".g.vcf"), ".g.vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    @Override
    public String getName()
    {
        return "GATK Genotype GVCFs";
    }

    @Override
    public String getDescription()
    {
        return "This will run GATK\'s GenotypeGVCF on a set of GVCF files.  Note: this cannot work against any VCF file - these are primarily VCFs created using GATK\'s HaplotypeCaller.";
    }

    @Nullable
    @Override
    public String getButtonJSHandler()
    {
        return null;
    }

    @Nullable
    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/sequenceanalysis/variantProcessing.view?showGenotypeGVCFs=1&outputFileIds=" + StringUtils.join(outputFileIds, ";"), c).getActionURL();
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return null;
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    @Override
    public Collection<String> getAllowableActionNames()
    {
        Set<String> allowableNames = new HashSet<>();
        allowableNames.add(getName());
        for (PipelineStepProvider<?> provider: SequencePipelineService.get().getProviders(VariantProcessingStep.class))
        {
            allowableNames.add(provider.getLabel());
        }

        return allowableNames;
    }

    public GenotypeGVCFHandler()
    {
//                ToolParameterDescriptor.create("fileBaseName", "Filename", "This is the basename that will be used for the output gzipped VCF", "textfield", null, "CombinedGenotypes"),
//                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-stand_call_conf"), "stand_call_conf", "Threshold For Calling Variants", "The minimum phred-scaled confidence threshold at which variants should be called", "ldk-numberfield", null, 30),
//                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--max_alternate_alleles"), "max_alternate_alleles", "Max Alternate Alleles", "Maximum number of alternate alleles to genotype", "ldk-integerfield", null, null),
//                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--includeNonVariantSites"), "includeNonVariantSites", "Include Non-Variant Sites", "If checked, all sites will be output into the VCF, instead of just those where variants are detected.  This can dramatically increase the size of the VCF.", "checkbox", null, false)
//                ToolParameterDescriptor.create("sharedPosixOptimizations", "Use Shared Posix Optimizations", "This enabled optimizations for large shared filesystems, such as lustre.", "checkbox", new JSONObject(){{
//                    put("checked", true);
//                }}, true),
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && (_gvcfFileType.isType(f.getFile()) || GenomicsDBImportHandler.CATEGORY.equals(f.getCategory()));
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
    public File getScatterJobOutput(JobContext ctx) throws PipelineJobException
    {
        return ProcessVariantsHandler.getScatterOutputByCategory(ctx, VCF_CATEGORY);
    }

    @Override
    public SequenceOutputFile createFinalSequenceOutput(PipelineJob job, File processed, List<SequenceOutputFile> inputFiles)
    {
        return ProcessVariantsHandler.createSequenceOutput(job, processed, inputFiles, VCF_CATEGORY);
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            ProcessVariantsHandler.initVariantProcessing(ctx.getJob(), ctx.getSequenceSupport(), inputFiles, ctx.getOutputDir());

            Set<Integer> genomeIds = new HashSet<>();
            for (SequenceOutputFile so : inputFiles)
            {
                genomeIds.add(so.getLibrary_id());
            }

            if (genomeIds.size() > 1)
            {
                throw new PipelineJobException("The selected files use more than one genome");
            }
            else if (genomeIds.isEmpty())
            {
                throw new PipelineJobException("No genome ID found for inputs");
            }

            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.forceSitesFile") != null)
            {
                int dataId = ctx.getParams().getInt("variantCalling.GenotypeGVCFs.forceSitesFile");
                ExpData data = ExperimentService.get().getExpData(dataId);
                if (data == null)
                {
                    throw new PipelineJobException("Unable to find ExpData with ID: " + dataId);
                }

                ctx.getJob().getLogger().debug("Caching ExpData: " + dataId);
                ctx.getSequenceSupport().cacheExpData(data);
            }

            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.exclude_intervals") != null)
            {
                int dataId = ctx.getParams().getInt("variantCalling.GenotypeGVCFs.exclude_intervals");
                ExpData data = ExperimentService.get().getExpData(dataId);
                if (data == null)
                {
                    throw new PipelineJobException("Unable to find ExpData with ID: " + dataId);
                }

                ctx.getJob().getLogger().debug("Caching ExpData: " + dataId);
                ctx.getSequenceSupport().cacheExpData(data);
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            ProcessVariantsHandler.Resumer resumer = ProcessVariantsHandler.Resumer.create(ctx);

            Set<Integer> genomeIds = new HashSet<>();
            List<File> inputVcfs = new ArrayList<>();
            for (SequenceOutputFile so : inputFiles)
            {
                genomeIds.add(so.getLibrary_id());
                inputVcfs.add(so.getFile());
            }

            if (genomeIds.size() > 1)
            {
                throw new PipelineJobException("The selected files use more than one genome");
            }
            else if (genomeIds.isEmpty())
            {
                throw new PipelineJobException("No genome ID found for inputs");
            }

            int genomeId = genomeIds.iterator().next();

            File outputVcf;
            if (resumer.isGenotypeGVCFsComplete())
            {
                outputVcf = resumer.getGenotypeGVCFsFile();
                ctx.getLogger().info("resuming GenotypeGVCFs from file: " + outputVcf.getPath());
            }
            else
            {
                outputVcf = runGenotypeGVCFs(job, ctx, resumer, inputVcfs, genomeId);
            }

            //run post processing, if needed
            File processed = ProcessVariantsHandler.processVCF(outputVcf, genomeId, ctx, resumer, false);
            if (processed == null)
            {
                ctx.getLogger().debug("adding GenotypeGVCFs output because no processing was selected");
                processed = outputVcf;
            }

            int sampleCount;
            try (VCFFileReader reader = new VCFFileReader(processed))
            {
                VCFHeader header = reader.getFileHeader();
                sampleCount = header.getSampleNamesInOrder().size();
            }

            ctx.getLogger().debug("adding sequence output: " + processed.getPath());
            SequenceOutputFile so1 = new SequenceOutputFile();
            so1.setName(processed.getName());
            GenotypeGVCFsWrapper wrapper = new GenotypeGVCFsWrapper(ctx.getLogger());
            so1.setDescription("GATK GenotypeGVCF output.  GATK Version: " + wrapper.getVersionString() + ".  Total samples: " + sampleCount);
            so1.setFile(processed);
            so1.setLibrary_id(genomeId);
            so1.setCategory(VCF_CATEGORY);
            so1.setContainer(job.getContainerId());
            so1.setCreated(new Date());
            so1.setModified(new Date());

            resumer.getFileManager().addSequenceOutput(so1);
            //TODO: rename output?

            resumer.markComplete(ctx);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        private String getBasename(JobContext ctx)
        {
            String basename = ctx.getParams().get("variantCalling.GenotypeGVCFs.fileBaseName") != null ? ctx.getParams().getString("variantCalling.GenotypeGVCFs.fileBaseName") : "CombinedGenotypes";
            basename = basename.replaceAll(".vcf.gz$", "");
            basename = basename.replaceAll(".vcf$", "");

            return basename;
        }

        private File getDoneFile(File vcf)
        {
            return new File(vcf.getPath() + ".done");
        }

        private File runGenotypeGVCFs(PipelineJob job, JobContext ctx, ProcessVariantsHandler.Resumer resumer, List<File> inputFiles, int genomeId) throws PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            File outDir = ctx.getOutputDir();
            String basename = getBasename(ctx);

            File outputVcf = new File(outDir, basename + ".vcf.gz");
            File outputVcfIdx = new File(outDir, basename + ".vcf.gz.tbi");
            File outputVcfDone = getDoneFile(outputVcf);

            if (inputFiles.size() > 250 || ctx.getJob().isSplitJob())
            {
                ctx.getLogger().debug("Too many inputs, will not add them individually so as to spare PipelineJob size");
            }
            else
            {
                for (File f : inputFiles)
                {
                    action.addInput(f, "Input Variants");
                }
            }

            boolean doCopyLocal = doCopyLocal(ctx.getParams());

            Set<File> toDelete = new HashSet<>();
            List<File> filesToProcess = new ArrayList<>();
            if (doCopyLocal)
            {
                ctx.getLogger().info("making local copies of gVCF/GenomicsDB files prior to genotyping");
                filesToProcess.addAll(GenotypeGVCFsWrapper.copyVcfsLocally(ctx, inputFiles, toDelete, outputVcfIdx.exists()));
            }
            else
            {
                filesToProcess.addAll(inputFiles);
            }

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(genomeId);
            if (genome == null)
            {
                throw new PipelineJobException("Unable to find cached genome for Id: " + genomeId);
            }

            File forceCallSitesFile = null;
            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.forceSitesFile") != null)
            {
                forceCallSitesFile = ctx.getSequenceSupport().getCachedData(ctx.getParams().getInt("variantCalling.GenotypeGVCFs.forceSitesFile"));
                if (!forceCallSitesFile.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + forceCallSitesFile.getPath());
                }

                forceCallSitesFile = getPossiblyLocalFile(ctx, forceCallSitesFile);
            }

            int maxSamplesPerWorkspace = 500;
            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.maxSamplesPerWorkspace") != null)
            {
                maxSamplesPerWorkspace = ctx.getParams().getInt("variantCalling.GenotypeGVCFs.maxSamplesPerWorkspace");
            }

            ctx.getFileManager().addIntermediateFile(outputVcfDone);
            if (outputVcfDone.exists())
            {
                ctx.getLogger().info("GenotypeGVCFs completed, will not re-process: " + outputVcfDone.getPath());
            }
            else
            {
                TreeSet<File> gvcfInputs = new TreeSet<>();
                TreeSet<File> genomicsDbInputs = new TreeSet<>();
                filesToProcess.forEach(f -> {
                    if (SequenceUtil.FILETYPE.gvcf.getFileType().isType(f))
                    {
                        gvcfInputs.add(f);
                    }
                    else if (AbstractGenomicsDBImportHandler.TILE_DB_FILETYPE.isType(f))
                    {
                        genomicsDbInputs.add(f);
                    }
                    else
                    {
                        throw new IllegalArgumentException("Unknown file type: " + f.getName());
                    }
                });

                if (!gvcfInputs.isEmpty())
                {
                    genomicsDbInputs.addAll(combineGvcfs(ctx, genome, gvcfInputs, maxSamplesPerWorkspace));
                }

                if (genomicsDbInputs.size() > 1)
                {
                    ctx.getLogger().info("Multiple inputs present, so genotypes will be called on each workspace individually, then merged");

                    // Run GenotypeGVCFs individually per workspace:
                    List<File> intermediateVcfs = new ArrayList<>();
                    int idx = 0;
                    for (File input : genomicsDbInputs)
                    {
                        idx++;
                        ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Running GenotypeGVCFs: " + idx + " of " + genomicsDbInputs.size());
                        File tempOutput = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(input.getName()) + ".temp.vcf.gz");
                        processOneInput(ctx, job, genome, input, tempOutput, forceCallSitesFile);
                        ctx.getFileManager().addIntermediateFile(tempOutput);
                        ctx.getFileManager().addIntermediateFile(new File(tempOutput.getPath() + ".tbi"));
                        ctx.getFileManager().addIntermediateFile(getDoneFile(tempOutput));
                        intermediateVcfs.add(tempOutput);
                    }

                    // Create the union of all sites to force calling:
                    ctx.getLogger().info("Creating VCF with union of sites");
                    ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Generating union of sites");
                    File sitesOnlyVcf = new File(ctx.getWorkingDirectory(), "sitesOnlyVcfForMerge.vcf.gz");
                    DISCVRSeqRunner runner = new DISCVRSeqRunner(ctx.getLogger());
                    List<String> mergeArgs = new ArrayList<>(runner.getBaseArgs("MergeVariantSites"));
                    intermediateVcfs.forEach(vcf -> {
                        mergeArgs.add("-V");
                        mergeArgs.add(vcf.getPath());
                    });

                    // NOTE: do not drop these sites for now, since we plan to process each technology separately and then merge.
                    //mergeArgs.add("-env");

                    mergeArgs.add("-O");
                    mergeArgs.add(sitesOnlyVcf.getPath());

                    mergeArgs.add("-R");
                    mergeArgs.add(genome.getWorkingFastaFile().getPath());

                    runner.execute(mergeArgs);
                    ctx.getFileManager().addIntermediateFile(sitesOnlyVcf);
                    ctx.getFileManager().addIntermediateFile(new File(sitesOnlyVcf.getPath() + ".tbi"));

                    // Run GenotypeGVCFs individually:
                    List<File> intermediateVcfsForcedSites = new ArrayList<>();
                    idx = 0;
                    for (File input : genomicsDbInputs)
                    {
                        idx++;
                        ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Second GenotypeGVCFs: " + idx + " of " + genomicsDbInputs.size());
                        File tempOutput = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(input.getName()) + ".tempForceSites.vcf.gz");
                        processOneInput(ctx, job, genome, input, tempOutput, sitesOnlyVcf);
                        ctx.getFileManager().addIntermediateFile(tempOutput);
                        ctx.getFileManager().addIntermediateFile(new File(tempOutput.getPath() + ".tbi"));
                        ctx.getFileManager().addIntermediateFile(getDoneFile(tempOutput));
                        intermediateVcfsForcedSites.add(tempOutput);
                    }

                    // Perform final merge:
                    ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Merging final VCFs");
                    new MergeVcfsAndGenotypesWrapper(ctx.getLogger()).execute(genome.getWorkingFastaFile(), intermediateVcfsForcedSites, outputVcf, null);
                    try
                    {
                        FileUtils.touch(getDoneFile(outputVcf));
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
                else
                {
                    ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Running GenotypeGVCFs, No Batches");
                    processOneInput(ctx, job, genome, genomicsDbInputs.iterator().next(), outputVcf, forceCallSitesFile);
                    try
                    {
                        FileUtils.touch(getDoneFile(outputVcf));
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }

            action.addOutput(outputVcf, "VCF", outputVcf.exists(), true);
            action.setEndTime(new Date());
            resumer.setGenotypeGVCFsComplete(action, outputVcf);

            if (!toDelete.isEmpty())
            {
                ctx.getLogger().info("deleting locally copied inputs");
                for (File f : toDelete)
                {
                    if (f.exists())
                    {
                        f.delete();
                    }
                }
            }

            return outputVcf;
        }

        private List<File> combineGvcfs(JobContext ctx, ReferenceGenome genome, Set<File> gvcfInputs, int maxSamplesPerWorkspace) throws PipelineJobException
        {
            List<List<File>> batches = Lists.partition(new ArrayList<>(gvcfInputs), maxSamplesPerWorkspace);
            ctx.getLogger().info("Initial gVCFs: " + gvcfInputs.size() + " will be split into " + batches.size() + " workspaces for import");

            List<File> ret = new ArrayList<>();
            int i = 0;
            for (List<File> batch : batches)
            {
                i++;
                Date start = new Date();
                ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "GenomicsDB: " + i + " of " + batches.size() + ", samples: " + batch.size());
                ctx.getLogger().info("Batch " + i + " of " + batches.size() + ", total samples: " + batch.size());
                ret.add(createWorkspace(ctx, genome, batch, i + "of" + batches.size()));

                ctx.getLogger().info("GenomicsDB Batch " + i + " Duration: " + DurationFormatUtils.formatDurationWords(new Date().getTime() - start.getTime(), true, true));
            }

            return ret;
        }

        private File createWorkspace(JobContext ctx, ReferenceGenome genome, List<File> vcfsToProcess, String id) throws PipelineJobException
        {
            File workspace = new File(ctx.getWorkingDirectory(), "genomicsDb" + id + ".gdb");
            File doneFile = getDoneFile(workspace);
            ctx.getFileManager().addIntermediateFile(workspace);
            ctx.getFileManager().addIntermediateFile(doneFile);

            if (doneFile.exists())
            {
                ctx.getLogger().debug("Workspace finished, skipping");
            }
            else
            {
                GenomicsDbImportWrapper wrapper = new GenomicsDbImportWrapper(ctx.getLogger());
                List<String> options = new ArrayList<>();
                options.add("--bypass-feature-reader");

                if (ctx.getParams().optBoolean("variantCalling.GenotypeGVCFs.disableFileLocking", false))
                {
                    ctx.getLogger().debug("Disabling file locking for TileDB");
                    wrapper.addToEnvironment("TILEDB_DISABLE_FILE_LOCKING", "1");
                }

                if (ctx.getParams().optBoolean("variantCalling.GenotypeGVCFs.sharedPosixOptimizations", false))
                {
                    options.add("--genomicsdb-shared-posixfs-optimizations");
                }

                Integer maxRam = SequencePipelineService.get().getMaxRam();
                int nativeMemoryBuffer = ctx.getParams().optInt("variantCalling.GenotypeGVCFs.nativeMemoryBuffer", 0);
                if (maxRam != null && nativeMemoryBuffer > 0)
                {
                    ctx.getLogger().info("Adjusting RAM based on memory buffer (" + nativeMemoryBuffer + "), from: " + maxRam);
                    maxRam = maxRam - nativeMemoryBuffer;

                    if (maxRam < 1)
                    {
                        throw new PipelineJobException("After adjusting for nativeMemoryBuffer, maxRam is less than 1: " + maxRam);
                    }
                    wrapper.setMaxRamOverride(maxRam);
                }

                Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
                if (maxThreads != null && maxThreads >= 2)
                {
                    options.add("--reader-threads");
                    options.add("2");
                }

                int batchSize = ctx.getParams().optInt("variantCalling.GenotypeGVCFs.batchSize", 50);
                options.add("--batch-size");
                options.add(String.valueOf(batchSize));

                try
                {
                    if (workspace.exists())
                    {
                        FileUtils.deleteDirectory(workspace);
                    }

                    List<Interval> intervals = getIntervals(ctx);
                    if (intervals != null)
                    {
                        int paddingWindow = ctx.getParams().optInt("variantCalling.GenotypeGVCFs.paddingWindow", 1000);
                        ctx.getLogger().info("Expanding intervals used in workspace by: " + paddingWindow);
                        SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
                        intervals = intervals.stream().map(i -> {
                            int length = dict.getSequence(i.getContig()).getSequenceLength();
                            int start = Math.max(1, i.getStart() - paddingWindow);
                            int end = Math.min(length, i.getEnd() + paddingWindow);

                            return new Interval(i.getContig(), start, end, i.isNegativeStrand(), i.getName());
                        }).collect(Collectors.toList());
                    }

                    wrapper.execute(genome, vcfsToProcess, workspace, intervals, options, false);

                    Files.touch(doneFile);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            return workspace;
        }

        private void processOneInput(JobContext ctx, PipelineJob job, ReferenceGenome genome, File inputVcf, File outputVcf, @Nullable File forceCallSites) throws PipelineJobException
        {
            ctx.getLogger().info("Running genotypeGVCFs for: " + inputVcf.getPath());
            Date start = new Date();

            GenotypeGVCFsWrapper wrapper = new GenotypeGVCFsWrapper(job.getLogger());
            List<String> toolParams = new ArrayList<>();
            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.stand_call_conf") != null)
            {
                toolParams.add("-stand-call-conf");
                toolParams.add(ctx.getParams().get("variantCalling.GenotypeGVCFs.stand_call_conf").toString());
            }

            // NOTE: added to side-step https://github.com/broadinstitute/gatk/issues/7938
            toolParams.add("-AX");
            toolParams.add("InbreedingCoeff");

            toolParams.add("-AX");
            toolParams.add("ExcessHet");

            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.maxGenotypeCount") != null)
            {
                toolParams.add("-max-genotype-count");
                toolParams.add(String.valueOf(ctx.getParams().get("variantCalling.GenotypeGVCFs.maxGenotypeCount")));
            }

            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.exclude_intervals") != null)
            {
                toolParams.add("-XL");
                int dataId = Integer.parseInt(ctx.getParams().get("variantCalling.GenotypeGVCFs.exclude_intervals").toString());
                File bed = ctx.getSequenceSupport().getCachedData(dataId);
                if (bed == null)
                {
                    throw new PipelineJobException("Unable to find ExpData: " + dataId);
                }

                bed = getPossiblyLocalFile(ctx, bed);

                toolParams.add(bed.getPath());
            }

            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.max_alternate_alleles") != null)
            {
                toolParams.add("--max-alternate-alleles");
                toolParams.add(ctx.getParams().get("variantCalling.GenotypeGVCFs.max_alternate_alleles").toString());

                toolParams.add("--genomicsdb-max-alternate-alleles");

                // See: https://gatk.broadinstitute.org/hc/en-us/articles/4418054384027-GenotypeGVCFs#--genomicsdb-max-alternate-alleles
                // "A typical value is 3 more than the --max-alternate-alleles value that's used by GenotypeGVCFs and larger differences result in more robustness to PCR-related indel errors"
                Integer maxAlt = ctx.getParams().getInt("variantCalling.GenotypeGVCFs.max_alternate_alleles") + 3;
                toolParams.add(maxAlt.toString());
            }

            if (ctx.getParams().optBoolean("variantCalling.GenotypeGVCFs.includeNonVariantSites"))
            {
                toolParams.add("--include-non-variant-sites");
            }

            if (forceCallSites != null)
            {
                toolParams.add("--force-output-intervals");
                toolParams.add(forceCallSites.getPath());
            }

            List<Interval> intervals = ProcessVariantsHandler.getIntervals(ctx);
            if (intervals != null)
            {
                if (intervals.size() > 5)
                {
                    ctx.getLogger().debug("Too many intervals, writing to file");
                    File intervalFile = new File(outputVcf.getParentFile(), "genotypeIntervals.list");
                    ctx.getFileManager().addIntermediateFile(intervalFile);
                    try (PrintWriter writer = PrintWriters.getPrintWriter(intervalFile))
                    {
                        intervals.forEach(interval -> {
                            writer.println(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                        });
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }

                    toolParams.add("-L");
                    toolParams.add(intervalFile.getPath());
                }
                else
                {
                    intervals.forEach(interval -> {
                        toolParams.add("-L");
                        toolParams.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                    });
                }
                toolParams.add("--only-output-calls-starting-in-intervals");
            }

            if (ctx.getParams().optBoolean("variantCalling.GenotypeGVCFs.disableFileLocking", false))
            {
                ctx.getLogger().debug("Disabling file locking for TileDB");
                wrapper.addToEnvironment("TILEDB_DISABLE_FILE_LOCKING", "1");
            }

            if (ctx.getParams().optBoolean("variantCalling.GenotypeGVCFs.sharedPosixOptimizations", false))
            {
                toolParams.add("--genomicsdb-shared-posixfs-optimizations");
            }

            Integer maxRam = SequencePipelineService.get().getMaxRam();
            int nativeMemoryBuffer = ctx.getParams().optInt("variantCalling.GenotypeGVCFs.nativeMemoryBuffer", 0);
            if (maxRam != null && nativeMemoryBuffer > 0)
            {
                ctx.getLogger().info("Adjusting RAM (" + maxRam + ") based on memory buffer (" + nativeMemoryBuffer + ")");
                maxRam = maxRam - nativeMemoryBuffer;

                if (maxRam < 1)
                {
                    throw new PipelineJobException("After adjusting for nativeMemoryBuffer, maxRam is less than 1: " + maxRam);
                }
                wrapper.setMaxRamOverride(maxRam);
            }

            if (getDoneFile(outputVcf).exists())
            {
                ctx.getLogger().info("VCF already created, skipping: " + outputVcf.getPath());
                return;
            }

            wrapper.execute(genome.getWorkingFastaFile(), outputVcf, toolParams, inputVcf);
            try
            {
                FileUtils.touch(getDoneFile(outputVcf));
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            ctx.getLogger().info("GenotypeGVCFs Duration: " + DurationFormatUtils.formatDurationWords(new Date().getTime() - start.getTime(), true, true));
        }
    }

    private boolean doCopyLocal(JSONObject params)
    {
        return params.optBoolean("variantCalling.GenotypeGVCFs.doCopyInputs", false);
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

    @Override
    public void doWork(List<SequenceOutputFile> inputFiles, JobContext ctx) throws PipelineJobException
    {
        ScatterGatherUtils.doCopyGvcfLocally(inputFiles, ctx);
        ScatterGatherUtils.possiblyCacheSupportFiles(ctx);
    }

    protected File getPossiblyLocalFile(JobContext ctx, File sourceFile)
    {
        File cacheDir = ScatterGatherUtils.getLocalCopyDir(ctx, false);
        if (!cacheDir.exists())
        {
            return sourceFile;
        }

        File cachedFile = new File(cacheDir, sourceFile.getName());
        if (cachedFile.exists())
        {
            ctx.getLogger().debug("Using locally cached file: " + cachedFile.getPath());
            return cachedFile;
        }

        return sourceFile;
    }
}
