package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.ScatterGatherUtils;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

abstract public class AbstractGenomicsDBImportHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> implements SequenceOutputHandler.TracksVCF, SequenceOutputHandler.HasCustomVariantMerge, VariantProcessingStep.MayRequirePrepareTask, VariantProcessingStep.SupportsScatterGather
{
    protected FileType _gvcfFileType = new FileType(List.of(".g.vcf"), ".g.vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);
    public static final FileType TILE_DB_FILETYPE = new FileType(List.of(".tdb"), ".tdb", false, FileType.gzSupportLevel.NO_GZ);

    public static final String CATEGORY = "GenomicsDB Workspace";
    public static final String EXISTING_WORKSPACE = "existingWorkspaceId";

    public AbstractGenomicsDBImportHandler(Module owner, String name, String description, LinkedHashSet<String> dependencies, List<ToolParameterDescriptor> parameters)
    {
        super(owner, name, description, dependencies, parameters);
    }

    protected static List<ToolParameterDescriptor> getToolParameters(boolean addCopyOption)
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>();

        if (addCopyOption)
        {
            ret.add(ToolParameterDescriptor.createExpDataParam(EXISTING_WORKSPACE, "Existing Workspace", "This is the workspace into which new samples will be merged", "sequenceanalysis-sequenceoutputfileselectorfield", new JSONObject()
            {{
                put("allowBlank", false);
                put("category", CATEGORY);
            }}, null));
        }

        ret.addAll(Arrays.asList(
            ToolParameterDescriptor.create("fileBaseName", "Filename", "This is the basename that will be used for the output gzipped VCF", "textfield", null, "CombinedGenotypes"),
            ToolParameterDescriptor.create("doCopyGVcfLocal", "Copy gVCFs To Working Directory", "If selected, the gVCFs will be copied to the working directory first, which can improve performance when working with a large set of files.", "checkbox", new JSONObject(){{
                put("checked", false);
            }}, false),
            ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--batch-size"), "batchSize", "Batch Size", "Batch size controls the number of samples for which readers are open at once and therefore provides a way to minimize memory consumption. However, it can take longer to complete. Use the consolidate flag if more than a hundred batches were used. This will improve feature read time. batchSize=0 means no batching (i.e. readers for all samples will be opened at once) Defaults to 0.", "ldk-integerfield", null, null),
            ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--reader-threads"), "readerThreads", "Reader Threads", "How many simultaneous threads to use when opening VCFs in batches; higher values may improve performance when network latency is an issue", "ldk-integerfield", null, null),
            ToolParameterDescriptor.create("disableFileLocking", "Disable File Locking", "Certain filesystems do not support file locking, including NFS and Lustre.  If your data will be processed on a filesystem that does not support locking, check this.", "checkbox", new JSONObject(){{
                put("checked", true);
            }}, true),
            ToolParameterDescriptor.create("sharedPosixOptimizations", "Use Shared Posix Optimizations", "This enabled optimizations for large shared filesystems, such as lustre.", "checkbox", new JSONObject(){{
                put("checked", true);
            }}, true),
            ToolParameterDescriptor.create("bypassFeatureReader", "Bypass Feature Reader", "If checked, rather than use the HTSJDK/Java reader, it will use a C-based implementation.", "checkbox", new JSONObject(){{
                put("checked", true);
            }}, true),
            ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--genomicsdb-segment-size"), "genomicsdbSegmentSize", "Genomicsdb Segment Size", "Reducing this value may help with memory issues", "ldk-integerfield", new JSONObject(){{
                put("minValue", 0);
            }}, null),
            ToolParameterDescriptor.create("nativeMemoryBuffer", "C++ Memory Buffer", "By default, the pipeline java processes are allocated nearly all of the requested RAM.  GenomicsDB requires memory for the C++ layer - this value (in GB) will be reserved for this.  We recommend about 15-25% of the total job RAM", "ldk-integerfield", new JSONObject(){{
                put("minValue", 0);
            }}, 36),
            ToolParameterDescriptor.create("consolidate", "Consolidate", "If importing data in batches, a new fragment is created for each batch. In case thousands of fragments are created, GenomicsDB feature readers will try to open ~20x as many files. Also, internally GenomicsDB would consume more memory to maintain bookkeeping data from all fragments. Use this flag to merge all fragments into one. Merging can potentially improve read performance, however overall benefit might not be noticeable as the top Java layers have significantly higher overheads. This flag has no effect if only one batch is used. Defaults to false.", "checkbox", new JSONObject(){{
                put("checked", true);
            }}, true),
            ToolParameterDescriptor.create("genomicsdbBatchSize", "Consolidate Batch Size", "This is passed to --batch-size of consolidate_genomicsdb_array, and can reduce memory usage. If a value of -1 is used, this will auto-calculate batch size using numberOfFragments/4", "ldk-numberfield", new JSONObject(){{
                put("minValue", 0);
            }}, null),
            ToolParameterDescriptor.create("scatterGather", "Scatter/Gather Options", "If selected, this job will be divided to run job per chromosome.  The final step will take the VCF from each intermediate step and combined to make a final VCF file.", "sequenceanalysis-variantscattergatherpanel", new JSONObject(){{
                put("defaultValue", "chunked");
            }}, false)
        ));

        if (addCopyOption)
        {
            ret.add(
                ToolParameterDescriptor.create("consolidateFirst", "Consolidate First", "If checked, this will run the standalone tool consolidate_genomicsdb_array on the input prior to running GATK.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false)
            );
        }

        return ret;
    }

    @Override
    public void validateScatter(VariantProcessingStep.ScatterGatherMethod method, PipelineJob job) throws IllegalArgumentException
    {
        validateNoSplitContigScatter(method, job);
    }

    public static void validateNoSplitContigScatter(VariantProcessingStep.ScatterGatherMethod method, PipelineJob job) throws IllegalArgumentException
    {
        if (!(job instanceof VariantProcessingJob vj))
        {
            return;
        }

        if (!vj.isScatterJob())
        {
            return;
        }

        if (vj.doAllowSplitContigs())
        {
            throw new IllegalArgumentException("This job does not support scatter methods with allowSplitContigs=true");
        }
    }

    @Override
    public SequenceOutputFile createFinalSequenceOutput(PipelineJob job, File mergedWorkspace, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        Set<Integer> libraryIds = new HashSet<>();
        inputFiles.forEach(x -> {
            if (x.getLibrary_id() != null)
                libraryIds.add(x.getLibrary_id());
        });

        if (libraryIds.isEmpty())
        {
            throw new IllegalArgumentException("No library ID defined for VCFs");
        }

        Set<Integer> readsetIds = new HashSet<>();
        inputFiles.forEach(x -> readsetIds.add(x.getReadset()));

        int sampleCount = getSamplesForWorkspace(mergedWorkspace.getParentFile()).size();

        SequenceOutputFile so1 = new SequenceOutputFile();
        so1.setName(getPipelineJob(job).getParameterJson().getString("fileBaseName"));
        so1.setFile(mergedWorkspace);
        so1.setLibrary_id(libraryIds.iterator().next());
        so1.setCategory(GenomicsDBImportHandler.CATEGORY);
        so1.setContainer(job.getContainerId());
        so1.setCreated(new Date());
        so1.setModified(new Date());
        so1.setReadset((readsetIds.size() != 1 ? null : readsetIds.iterator().next()));
        so1.setDescription("Total samples: " + sampleCount);

        return so1;
    }

    public static Collection<String> getSamplesForWorkspace(File workspace) throws PipelineJobException
    {
        //See: https://github.com/GenomicsDB/GenomicsDB/wiki/Importing-VCF-data-into-GenomicsDB
        try (BufferedReader reader = IOUtil.openFileForBufferedUtf8Reading(new File(workspace, "callset.json")))
        {
            StringBuilder contentBuilder = new StringBuilder();
            reader.lines().forEach(s -> contentBuilder.append(s).append("\n"));

            JSONObject json = new JSONObject(contentBuilder.toString());

            List<String> ret = new ArrayList<>();
            JSONArray samples = json.getJSONArray("callsets");
            for (int i = 0; i < samples.length();i++)
            {
                ret.add(samples.getJSONObject(i).getString("sample_name"));
            }

            return ret;

        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && _gvcfFileType.isType(o.getFile());
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
        return ProcessVariantsHandler.getScatterOutputByCategory(ctx, GenomicsDBImportHandler.CATEGORY);
    }

    protected File getWorkspaceOutput(File outDir, String basename)
    {
        basename = FileUtil.makeLegalName(basename);
        return new File(outDir, basename + (basename.endsWith(".") ? "" : ".") + "gdb");
    }

    protected List<Interval> getIntervals(JobContext ctx)
    {
        if (ctx.getJob() instanceof VariantProcessingJob job)
        {

            return job.getIntervalsForTask();
        }

        return null;
    }

    protected List<Interval> getIntervalsOrFullGenome(JobContext ctx, ReferenceGenome genome)
    {
        List<Interval> ret = getIntervals(ctx);
        if (ret != null)
        {
            return ret;
        }

        ret = new ArrayList<>();
        SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
        for (SAMSequenceRecord rec : dict.getSequences())
        {
            ret.add(new Interval(rec.getSequenceName(), 1, rec.getSequenceLength()));
        }

        return ret;
    }

    protected VariantProcessingJob getPipelineJob(PipelineJob job)
    {
        return (VariantProcessingJob) job;
    }

    @Override
    public File performVariantMerge(TaskFileManager manager, RecordedAction action, SequenceOutputHandler<SequenceOutputProcessor> handler, PipelineJob job) throws PipelineJobException
    {
        Map<String, List<Interval>> jobToIntervalMap = getPipelineJob(job).getJobToIntervalMap();
        job.setStatus(PipelineJob.TaskStatus.running, "Creating merged workspace from " + jobToIntervalMap.size() + " jobs");

        VariantProcessingJob variantProcessingJob = getPipelineJob(job);
        File destinationWorkspace = getWorkspaceOutput(variantProcessingJob.getDataDirectory(), variantProcessingJob.getParameterJson().getString("fileBaseName"));
        if (!destinationWorkspace.exists())
        {
            destinationWorkspace.mkdirs();
        }

        File overallDone = new File(destinationWorkspace, "merge.done");
        manager.addIntermediateFile(overallDone);
        if (overallDone.exists())
        {
            job.getLogger().info("workspace has already been merged, resuming");
            return getMarkerFile(destinationWorkspace);
        }

        // The per-contig folders should have been copied during the jobs:
        Map<String, File> scatterOutputs = getPipelineJob(job).getScatterJobOutputs();
        for (String name : jobToIntervalMap.keySet())
        {
            File overallCopyDone = new File(variantProcessingJob.getDataDirectory(), name + "/copyToWebserver.done");
            if (overallCopyDone.exists())
            {
                overallCopyDone.delete();
            }

            //Iterate the contig folders we expect:
            for (Interval i : jobToIntervalMap.get(name))
            {
                String contigFolder = getFolderNameFromInterval(i);
                job.getLogger().info("Inspecting contig folder: " + contigFolder);
                File copyDone = new File(destinationWorkspace, contigFolder + ".copy.done");
                if (copyDone.exists())
                {
                    copyDone.delete();
                }

                File expectedFolder = new File(destinationWorkspace, contigFolder);
                if (!expectedFolder.exists())
                {
                    Set<String> contigsInInput = getContigsInInputs(variantProcessingJob.getInputFiles(), job.getLogger());
                    if (!contigsInInput.contains(i.getContig()))
                    {
                        job.getLogger().info("Contig not present in the input gVCFs, skipping: " + i.getContig());
                        continue;
                    }

                    throw new PipelineJobException("Unable to find expected file: " + expectedFolder.getPath());
                }
            }
        }

        try
        {
            FileUtils.touch(overallDone);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return getMarkerFile(destinationWorkspace);
    }

    private Set<String> _contigsInInputs = null;

    Set<String> getContigsInInputs(List<File> inputVCFs, Logger log) throws PipelineJobException
    {
        if (_contigsInInputs == null)
        {
            Set<String> ret = new HashSet<>();
            for (File f : inputVCFs)
            {
                ret.addAll(SequenceUtil.getContigsInVcf(f));
            }

            log.info("Total contigs in input VCFs: " + ret.size());

            _contigsInInputs = ret;
        }

        return _contigsInInputs;
    }

    private void copyTopLevelFiles(PipelineJob job, File sourceWorkspace, File destinationWorkspace, boolean removeOtherFiles, boolean overwriteExisting) throws IOException, PipelineJobException
    {
        job.getLogger().info("Copying top-level files from: " + sourceWorkspace.getPath());
        if (removeOtherFiles)
        {
            for (File f : destinationWorkspace.listFiles())
            {
                if (!f.isDirectory())
                {
                    job.getLogger().debug("deleting existing top-level file: " + f.getPath());
                    f.delete();
                }
            }
        }

        for (String fn : Arrays.asList("callset.json", "vidmap.json", "vcfheader.vcf", "__tiledb_workspace.tdb"))
        {
            File source = new File(sourceWorkspace, fn);
            File dest = new File(destinationWorkspace, fn);
            if (dest.exists())
            {
                if (!overwriteExisting)
                {
                    job.getLogger().debug("workspace file exists, will not overwrite: " + dest.getPath());
                    continue;
                }

                dest.delete();
            }

            Set<PosixFilePermission> expected = PosixFilePermissions.fromString("rw-rw----");
            if (!Files.getPosixFilePermissions(source.toPath()).containsAll(expected))
            {
                job.getLogger().debug("Setting POSIX permissions on file: " + source.getPath());
                Files.setPosixFilePermissions(source.toPath(), expected);
            }

            FileUtils.copyFile(source, dest);
            if (!Files.getPosixFilePermissions(dest.toPath()).containsAll(expected))
            {
                job.getLogger().debug("Setting POSIX permissions on file: " + dest.getPath());
                Files.setPosixFilePermissions(dest.toPath(), expected);
            }
        }

        File metaDir = new File(sourceWorkspace, "genomicsdb_meta_dir");
        File metaDirDest = new File(sourceWorkspace, "genomicsdb_meta_dir");
        if (metaDirDest.exists())
        {
            if (!overwriteExisting)
            {
                job.getLogger().debug("workspace file exists, will not overwrite: " + metaDirDest.getPath());
            }
            else
            {
                FileUtils.deleteDirectory(metaDirDest);
            }
        }

        if (metaDir.exists() && !metaDirDest.exists())
        {
            job.getLogger().debug("Copying directory with rsync: " + metaDir.getPath());
            new SimpleScriptWrapper(job.getLogger()).execute(Arrays.asList(
                    "rsync", "-r", "-a", "--delete", "--no-owner", "--no-group", "--no-perms", "--chmod=D2770,F660", metaDir.getPath(), metaDirDest.getParentFile().getPath()
            ));
        }
    }

    protected File getMarkerFile(File workspace)
    {
        return new File(workspace, "__tiledb_workspace.tdb");
    }

    public static String getFolderNameFromInterval(Interval i)
    {
        return i.getContig() + "$" + i.getStart() + "$" + i.getEnd();
    }

    public class Processor implements SequenceOutputProcessor
    {
        private final boolean _append;

        public Processor(boolean append)
        {
            _append = append;
        }

        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            Set<Integer> genomeIds = new HashSet<>();
            Set<String> uniqueSamples = new HashSet<>();

            if (_append)
            {
                File workspace = getSourceWorkspace(ctx.getParams(), ctx.getSequenceSupport());
                uniqueSamples.addAll(getSamplesForWorkspace(workspace));
                ctx.getJob().getLogger().info("Samples in the existing workspace: " + uniqueSamples.size());
            }

            for (SequenceOutputFile so : inputFiles)
            {
                genomeIds.add(so.getLibrary_id());

                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    List<String> samples = reader.getFileHeader().getGenotypeSamples();

                    if (CollectionUtils.containsAny(uniqueSamples, samples))
                    {
                        samples.retainAll(uniqueSamples);
                        throw new PipelineJobException("Duplicate samples found in: " + so.getFile().getName() + ", sample were:" + StringUtils.join(samples, ","));
                    }

                    uniqueSamples.addAll(samples);
                }
            }

            if (genomeIds.size() > 1)
            {
                throw new PipelineJobException("The selected files use more than one genome");
            }
        }

        private File getSourceWorkspace(JSONObject params, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            int existingId = params.optInt(EXISTING_WORKSPACE, -1);
            if (existingId < 0)
            {
                throw new PipelineJobException("No workspace provided for merge");
            }

            File ret = support.getCachedData(existingId);
            if (ret == null)
            {
                throw new PipelineJobException("Source workspace was not cached");
            }

            return ret.getParentFile();
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            ctx.getLogger().info("Starting GenomicsDbImport: " + (_append ? "append" : "import"));
            if (inputFiles.isEmpty())
            {
                throw new PipelineJobException("No input files found");
            }

            boolean doCopyGVcfLocal = doCopyLocal(ctx.getParams());

            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            Set<Integer> genomeIds = new HashSet<>();
            List<File> inputVcfs = new ArrayList<>();
            for (SequenceOutputFile so : inputFiles)
            {
                genomeIds.add(so.getLibrary_id());
                inputVcfs.add(so.getFile());
                action.addInput(so.getFile(), "Input gVCF");
            }

            if (genomeIds.size() > 1)
            {
                throw new PipelineJobException("The selected files use more than one genome");
            }
            else if (genomeIds.isEmpty())
            {
                throw new PipelineJobException("No genome found");
            }

            int genomeId = genomeIds.iterator().next();
            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(genomeId);
            if (genome == null)
            {
                throw new PipelineJobException("Unable to find cached genome for Id: " + genomeId);
            }

            File workingDestinationWorkspaceFolder = getWorkspaceOutput(ctx.getOutputDir(), ctx.getParams().getString("fileBaseName"));

            Set<File> toDelete = new HashSet<>();
            File doneFile = new File(workingDestinationWorkspaceFolder, "genomicsdb.done");
            boolean genomicsDbCompleted = doneFile.exists();

            ctx.getFileManager().addIntermediateFile(doneFile);
            if (_append)
            {
                if (genomicsDbCompleted)
                {
                    ctx.getLogger().debug("GenomicsDB previously completed, resuming");
                }

                File sourceWorkspace = getSourceWorkspace(ctx.getParams(), ctx.getSequenceSupport());
                copyWorkspace(ctx, sourceWorkspace, workingDestinationWorkspaceFolder, genome, toDelete, !genomicsDbCompleted, !genomicsDbCompleted, !genomicsDbCompleted);
            }
            else
            {
                if (genomicsDbCompleted)
                {
                    ctx.getLogger().debug("GenomicsDB previously completed, resuming");
                }
                else
                {
                    if (workingDestinationWorkspaceFolder.exists())
                    {
                        ctx.getLogger().info("Deleting existing output folder: " + workingDestinationWorkspaceFolder.getPath());
                        try
                        {
                            FileUtils.deleteDirectory(workingDestinationWorkspaceFolder);
                        }
                        catch (IOException e)
                        {
                            throw new PipelineJobException(e);
                        }
                    }
                }
            }

            List<File> vcfsToProcess = new ArrayList<>();
            if (doCopyGVcfLocal)
            {
                ctx.getLogger().info("making local copies of gVCFs");
                vcfsToProcess.addAll(GenotypeGVCFsWrapper.copyVcfsLocally(ctx, inputVcfs, toDelete, genomicsDbCompleted));
            }
            else
            {
                vcfsToProcess.addAll(inputVcfs);
            }

            GenomicsDbImportWrapper wrapper = new GenomicsDbImportWrapper(ctx.getLogger());
            List<String> options = new ArrayList<>(getClientCommandArgs(ctx.getParams()));

            if (ctx.getParams().optBoolean("sharedPosixOptimizations", false))
            {
                options.add("--genomicsdb-shared-posixfs-optimizations");
            }

            if (ctx.getParams().optBoolean("bypassFeatureReader", true))
            {
                options.add("--bypass-feature-reader");
            }

            if (ctx.getParams().optBoolean("disableFileLocking", false))
            {
                ctx.getLogger().debug("Disabling file locking for TileDB");
                wrapper.addToEnvironment("TILEDB_DISABLE_FILE_LOCKING", "1");
            }

            if (!genomicsDbCompleted)
            {
                try
                {
                    List<Interval> intervals = getIntervals(ctx);

                    if (ctx.getParams().optBoolean("consolidateFirst", false))
                    {
                        ctx.getLogger().info("Will pre-consolidate the workspace using consolidate_genomicsdb_array");
                        doConsolidate(ctx, workingDestinationWorkspaceFolder, genome);
                    }

                    Integer maxRam = SequencePipelineService.get().getMaxRam();
                    Integer nativeMemoryBuffer = ctx.getParams().optInt("nativeMemoryBuffer", 0);
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

                    wrapper.execute(genome, vcfsToProcess, workingDestinationWorkspaceFolder, intervals, options, _append);

                    if (ctx.getParams().optBoolean("consolidate", true))
                    {
                        ctx.getLogger().info("Will consolidate the workspace using consolidate_genomicsdb_array");
                        doConsolidate(ctx, workingDestinationWorkspaceFolder, genome);
                    }

                    FileUtils.touch(doneFile);
                    ctx.getLogger().debug("GenomicsDB complete, touching file: " + doneFile.getPath());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                ctx.getLogger().info("Resuming from existing file: " + workingDestinationWorkspaceFolder.getPath());
            }
            ctx.getFileManager().addIntermediateFile(doneFile);

            File markerFile = getMarkerFile(workingDestinationWorkspaceFolder);
            if (!markerFile.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + markerFile.getPath());
            }

            List<Interval> intervals = getIntervalsOrFullGenome(ctx, genome);
            for (Interval i : intervals)
            {
                File destContigFolder = new File(workingDestinationWorkspaceFolder, getFolderNameFromInterval(i));
                reportFragmentsPerContig(ctx, destContigFolder, i.getContig());
            }

            ctx.getLogger().debug("adding sequence output: " + workingDestinationWorkspaceFolder.getPath());
            SequenceOutputFile so1 = new SequenceOutputFile();
            so1.setName(workingDestinationWorkspaceFolder.getName());

            int sampleCount = getSamplesForWorkspace(workingDestinationWorkspaceFolder).size();

            if (_append)
            {
                int initialSampleCount = getSamplesForWorkspace(getSourceWorkspace(ctx.getParams(), ctx.getSequenceSupport())).size();

                so1.setDescription("GATK GenomicsDB, original samples: " + initialSampleCount + ", appended " + inputFiles.size() + " gVCFs, final samples: " + sampleCount + ".  GATK Version: " + wrapper.getVersionString());
            }
            else
            {
                so1.setDescription("GATK GenomicsDB, created from " + inputFiles.size() + " files, " + sampleCount + " samples.  GATK Version: " + wrapper.getVersionString());
            }

            // rsync to source during the job, so we take care of this expensive step prior to closing the resumer.
            // also we dont want those files tracked as outputs, since there will be many:
            File workspaceLocalDir = getWorkspaceOutput(ctx.getSourceDirectory(true), ctx.getParams().getString("fileBaseName"));
            ctx.getLogger().info("Copying local workspace to local job dir: " + workspaceLocalDir.getPath());

            File copyToSourceDone = getCopyToSourceDone(ctx);

            if (!copyToSourceDone.exists())
            {
                copyWorkspace(ctx, workingDestinationWorkspaceFolder, workspaceLocalDir, genome, toDelete, true, false, false);

                try
                {
                    FileUtils.touch(copyToSourceDone);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                ctx.getLogger().debug("Workspace has already been copied locally");
            }

            markerFile = getMarkerFile(workspaceLocalDir);
            if (!markerFile.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + markerFile.getPath());
            }

            so1.setFile(markerFile);
            so1.setLibrary_id(genomeId);
            so1.setCategory(CATEGORY);
            so1.setContainer(ctx.getJob().getContainerId());
            so1.setCreated(new Date());
            so1.setModified(new Date());
            ctx.addSequenceOutput(so1);
            action.addOutput(markerFile, "GenomicsDB Workspace", false);

            SequenceUtil.deleteFolderWithRm(ctx.getLogger(), workingDestinationWorkspaceFolder);

            action.setEndTime(new Date());
            ctx.addActions(action);

            if (!toDelete.isEmpty())
            {
                ctx.getLogger().info("deleting locally copied files: " + toDelete.size());
                for (File f : toDelete)
                {
                    ctx.getLogger().debug(f.getPath());
                    f.delete();
                }
            }
        }
    }

    private void doConsolidate(JobContext ctx, File workingDestinationWorkspaceFolder, ReferenceGenome genome) throws PipelineJobException
    {
        List<String> baseArgs = new ArrayList<>();
        baseArgs.add(SequencePipelineService.get().getExeForPackage("GENOMICSDB_PATH", "consolidate_genomicsdb_array").getPath());

        baseArgs.add("-w");
        baseArgs.add(workingDestinationWorkspaceFolder.getPath());

        if (ctx.getParams().optBoolean("sharedPosixOptimizations", false))
        {
            baseArgs.add("--shared-posixfs-optimizations");
        }

        if (StringUtils.trimToNull(ctx.getParams().optString("genomicsdbSegmentSize")) != null)
        {
            baseArgs.add("--segment-size");
            baseArgs.add(String.valueOf(ctx.getParams().get("genomicsdbSegmentSize")));
        }

        int batchSize = ctx.getParams().optInt("genomicsdbBatchSize", 0);
        if (batchSize > 0)
        {
            baseArgs.add("-b");
            baseArgs.add(String.valueOf(batchSize));
        }

        List<Interval> intervals = getIntervalsOrFullGenome(ctx, genome);
        for (Interval i : intervals)
        {
            File contigFolder = new File(workingDestinationWorkspaceFolder, getFolderNameFromInterval(i));
            ctx.getLogger().info("Consolidating contig folder: " + contigFolder);
            ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Consolidating contig folder: " + contigFolder.getName());

            List<String> toRun = new ArrayList<>(baseArgs);
            toRun.add("-a");
            toRun.add(contigFolder.getName());

            if (batchSize == -1)
            {
                int totalFragments = getFragmentsPerContig(contigFolder).size();
                int inferredBatchSize = Math.max(1, totalFragments / 4);

                ctx.getLogger().debug("Inferring batch size from fragments (" + totalFragments + "). Using: " + inferredBatchSize);
                if (inferredBatchSize > 1)
                {
                    baseArgs.add("-b");
                    baseArgs.add(String.valueOf(inferredBatchSize));
                }
            }

            new SimpleScriptWrapper(ctx.getLogger()).execute(toRun);

            reportFragmentsPerContig(ctx, contigFolder, i.getContig());
        }
    }

    private void copyWorkspace(JobContext ctx, File sourceWorkspace, File destinationWorkspaceFolder, ReferenceGenome genome, Collection<File> toDelete, boolean alwaysPerformRsync, boolean overwriteTopLevelFiles, boolean removeExistingTopLevelFiles) throws PipelineJobException
    {
        if (!destinationWorkspaceFolder.exists())
        {
            destinationWorkspaceFolder.mkdirs();
        }

        boolean haveCopiedTopLevelFiles = false;

        List<Interval> intervals = getIntervalsOrFullGenome(ctx, genome);
        for (Interval i : intervals)
        {
            try
            {
                File sourceFolder = new File(sourceWorkspace, getFolderNameFromInterval(i));
                File destContigFolder = new File(destinationWorkspaceFolder, sourceFolder.getName());

                File copyDone = new File(destContigFolder.getPath() + ".copy.done");
                toDelete.add(copyDone);

                if (copyDone.exists())
                {
                    if (alwaysPerformRsync)
                    {
                        ctx.getLogger().debug("deleting existing done file: " + copyDone.getPath());
                        copyDone.delete();
                    }
                    else
                    {
                        ctx.getLogger().info("has been copied, skipping: " + i.getContig());
                        assertContigFoldersEqual(sourceFolder, destContigFolder);
                        reportFragmentsPerContig(ctx, destContigFolder, i.getContig());
                        continue;
                    }
                }

                if (!haveCopiedTopLevelFiles)
                {
                    copyTopLevelFiles(ctx.getJob(), sourceWorkspace, destinationWorkspaceFolder, removeExistingTopLevelFiles, overwriteTopLevelFiles);
                    haveCopiedTopLevelFiles = true;
                }

                if (!sourceFolder.exists())
                {
                    throw new PipelineJobException("Unable to find expected file: " + sourceFolder.getPath());
                }

                if (SystemUtils.IS_OS_WINDOWS)
                {
                    if (destContigFolder.exists())
                    {
                        ctx.getLogger().info("Target exists, deleting: " + destContigFolder.getPath());
                        FileUtils.deleteDirectory(destContigFolder);
                    }

                    ctx.getLogger().info("copying contig folder: " + i.getContig());

                    FileUtils.copyDirectory(sourceFolder, destContigFolder);
                }
                else
                {
                    ctx.getLogger().debug("Copying directory with rsync: " + sourceFolder.getPath());
                    //NOTE: since neither path will end in slashes, rsync to the parent folder should result in the correct placement
                    new SimpleScriptWrapper(ctx.getLogger()).execute(Arrays.asList(
                            "rsync", "-r", "-a", "--delete", "--no-owner", "--no-group", "--no-perms", "--chmod=D2770,F660", sourceFolder.getPath(), destContigFolder.getParentFile().getPath()
                    ));
                }

                FileUtils.touch(copyDone);
                assertContigFoldersEqual(sourceFolder, destContigFolder);
                reportFragmentsPerContig(ctx, destContigFolder, i.getContig());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    private File getCopyToSourceDone(JobContext ctx)
    {
        return new File(ctx.getSourceDirectory(), "copyToWebserver.done");
    }

    private void assertContigFoldersEqual(File sourceFolder, File destContigFolder) throws IllegalArgumentException
    {
        List<String> sourceFiles = getFragmentsPerContig(sourceFolder);
        List<String> destFiles = getFragmentsPerContig(destContigFolder);

        if (!sourceFiles.equals(destFiles))
        {
            throw new IllegalArgumentException("Source and destination contig files not equal for: " + destContigFolder.getPath());
        }
    }

    private void reportFragmentsPerContig(JobContext ctx, File destContigFolder, String contigName)
    {
        List<String> children = getFragmentsPerContig(destContigFolder);
        if (children == null)
        {
            ctx.getLogger().warn("expected folder not found: " + destContigFolder.getPath());
        }
        else
        {
            ctx.getLogger().info(contigName + ", total fragments: " + children.size()+ ", in: " + destContigFolder.getPath());
        }
    }

    private List<String> getFragmentsPerContig(File destContigFolder)
    {
        if (destContigFolder.exists())
        {
            List<String> children = Arrays.stream(destContigFolder.listFiles(x -> {
                return  x.isDirectory() && !"genomicsdb_meta_dir".equals(x.getName());
            })).map(File::getName).collect(Collectors.toList());

            Collections.sort(children);

            return children;
        }

        return null;
    }

    private boolean doCopyLocal(JSONObject params)
    {
        return params.optBoolean("doCopyGVcfLocal", false);
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
    }
}
