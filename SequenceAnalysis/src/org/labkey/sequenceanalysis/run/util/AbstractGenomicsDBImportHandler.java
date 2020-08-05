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
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.analysis.GenotypeGVCFHandler;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract public class AbstractGenomicsDBImportHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> implements SequenceOutputHandler.TracksVCF, SequenceOutputHandler.HasCustomVariantMerge, VariantProcessingStep.MayRequirePrepareTask
{
    protected FileType _gvcfFileType = new FileType(Arrays.asList(".g.vcf"), ".g.vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);
    public static final FileType TILE_DB_FILETYPE = new FileType(Arrays.asList(".tdb"), ".tdb", false, FileType.gzSupportLevel.NO_GZ);

    public static final String CATEGORY = "GenomicsDB Workspace";
    public static final String EXISTING_WORKSPACE = "existingWorkspaceId";

    public AbstractGenomicsDBImportHandler(Module owner, String name, String description, LinkedHashSet<String> dependencies, List<ToolParameterDescriptor> parameters)
    {
        super(owner, name, description, dependencies, parameters);
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
        if (ctx.getJob() instanceof VariantProcessingJob)
        {
            VariantProcessingJob job = (VariantProcessingJob) ctx.getJob();

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

    private void deleteSourceWorkspaces(VariantProcessingJob variantProcessingJob) throws PipelineJobException
    {
        Map<String, File> scatterOutputs = variantProcessingJob.getScatterJobOutputs();
        Map<String, List<Interval>> jobToIntervalMap = variantProcessingJob.getJobToIntervalMap();

        for (String name : jobToIntervalMap.keySet())
        {
            if (!scatterOutputs.containsKey(name))
            {
                throw new PipelineJobException("Missing output for interval/contig: " + name);
            }

            File sourceWorkspace = scatterOutputs.get(name).getParentFile();
            if (sourceWorkspace.exists())
            {
                try
                {
                    FileUtils.deleteDirectory(sourceWorkspace);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }
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
        if (overallDone.exists())
        {
            job.getLogger().info("workspace has already been merged, resuming");
            return getMarkerFile(destinationWorkspace);
        }

        Map<String, File> scatterOutputs = getPipelineJob(job).getScatterJobOutputs();
        boolean copiedTopLevelFiles = false;
        Set<File> toDelete = new HashSet<>();
        for (String name : jobToIntervalMap.keySet())
        {
            if (!scatterOutputs.containsKey(name))
            {
                throw new PipelineJobException("Missing output for interval/contig: " + name);
            }

            File sourceWorkspace = scatterOutputs.get(name).getParentFile();
            if (!sourceWorkspace.exists())
            {
                throw new PipelineJobException("Missing output: " + sourceWorkspace.getPath());
            }

            //Iterate the contig folders we expect:
            try
            {
                for (Interval i : jobToIntervalMap.get(name))
                {
                    String contigFolder = getFolderNameFromInterval(i);
                    job.getLogger().info("Copying contig folder: " + contigFolder);
                    File copyDone = new File(destinationWorkspace, contigFolder + ".copy.done");
                    toDelete.add(copyDone);

                    if (copyDone.exists())
                    {
                        job.getLogger().info("has been copied, skipping");
                        continue;
                    }

                    if (!copiedTopLevelFiles)
                    {
                        copyToLevelFiles(job, sourceWorkspace, destinationWorkspace);
                        copiedTopLevelFiles = true;
                    }

                    File sourceFolder = new File(sourceWorkspace, contigFolder);
                    if (!sourceFolder.exists())
                    {
                        Set<String> contigsInInput = getContigsInInputs(variantProcessingJob.getInputFiles(), job.getLogger());
                        if (!contigsInInput.contains(i.getContig()))
                        {
                            job.getLogger().info("Contig not present in the input gVCFs, skipping: " + i.getContig());
                            continue;
                        }

                        throw new PipelineJobException("Unable to find expected file: " + sourceFolder.getPath());
                    }

                    File destContigFolder = new File(destinationWorkspace, sourceFolder.getName());
                    if (destContigFolder.exists())
                    {
                        throw new PipelineJobException("Target exists, perhaps it was already copied: " + destContigFolder.getPath() + ".  Expected to find marker file: " + copyDone.getPath());
                    }

                    FileUtils.moveDirectory(sourceFolder, destContigFolder);
                    FileUtils.touch(copyDone);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        toDelete.forEach(File::delete);
        deleteSourceWorkspaces(variantProcessingJob);

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

    private void copyToLevelFiles(PipelineJob job, File sourceWorkspace, File destinationWorkspace) throws IOException
    {
        job.getLogger().info("Copying top-level files");
        for (String fn : Arrays.asList("callset.json", "vidmap.json", "vcfheader.vcf", "__tiledb_workspace.tdb"))
        {
            File source = new File(sourceWorkspace, fn);
            File dest = new File(destinationWorkspace, fn);
            if (dest.exists())
            {
                dest.delete();
            }

            FileUtils.copyFile(source, dest);
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
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            Set<Integer> genomeIds = new HashSet<>();
            Set<String> uniqueSamples = new HashSet<>();

            if (_append)
            {
                File workspace = getSourceWorkspace(params, support);
                uniqueSamples.addAll(getSamplesForWorkspace(workspace));
                job.getLogger().info("Samples in the existing workspace: " + uniqueSamples.size());
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

            int genomeId = genomeIds.iterator().next();
            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(genomeId);
            if (genome == null)
            {
                throw new PipelineJobException("Unable to find cached genome for Id: " + genomeId);
            }

            File destinationWorkspaceFolder = getWorkspaceOutput(ctx.getOutputDir(), ctx.getParams().getString("fileBaseName"));

            Set<File> toDelete = new HashSet<>();
            File doneFile = new File(destinationWorkspaceFolder, "genomicsdb.done");
            File startedFile = new File(destinationWorkspaceFolder.getParentFile(), "genomicsdb.started");
            boolean genomicsDbCompleted = doneFile.exists();
            boolean genomicsDbStarted = startedFile.exists();
            ctx.getFileManager().addIntermediateFile(doneFile);
            ctx.getFileManager().addIntermediateFile(startedFile);
            if (_append)
            {
                if (genomicsDbCompleted)
                {
                    ctx.getLogger().debug("GenomicsDB previously completed, resuming");
                }

                if (!destinationWorkspaceFolder.exists())
                {
                    destinationWorkspaceFolder.mkdirs();
                }

                boolean copiedTopLevelFiles = false;
                File sourceWorkspace = getSourceWorkspace(ctx.getParams(), ctx.getSequenceSupport());
                List<Interval> intervals = getIntervalsOrFullGenome(ctx, genome);
                for (Interval i : intervals)
                {
                    try
                    {
                        File sourceFolder = new File(sourceWorkspace, getFolderNameFromInterval(i));
                        File destContigFolder = new File(destinationWorkspaceFolder, sourceFolder.getName());
                        File copyDone = new File(destContigFolder.getPath() + ".copy.done");
                        toDelete.add(copyDone);

                        //NOTE: if GenomicsDB has started, but dies mid-job, the resulting workspace probably cannot be resumed
                        if (!genomicsDbStarted && copyDone.exists())
                        {
                            ctx.getLogger().info("has been copied, skipping");
                            continue;
                        }

                        //Allow the above to complete so we track the .done files
                        if (genomicsDbCompleted)
                        {
                            continue;
                        }

                        if (!copiedTopLevelFiles)
                        {
                            copyToLevelFiles(ctx.getJob(), sourceWorkspace, destinationWorkspaceFolder);
                            copiedTopLevelFiles = true;
                        }

                        if (!sourceFolder.exists())
                        {
                            throw new PipelineJobException("Unable to find expected file: " + sourceFolder.getPath());
                        }

                        if (destContigFolder.exists())
                        {
                            ctx.getLogger().info("Target exists, deleting: " + destContigFolder.getPath());
                            FileUtils.deleteDirectory(destContigFolder);
                        }

                        FileUtils.copyDirectory(sourceFolder, destContigFolder);
                        FileUtils.touch(copyDone);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }
            else
            {
                if (genomicsDbCompleted)
                {
                    ctx.getLogger().debug("GenomicsDB previously completed, resuming");
                }
                else
                {
                    if (destinationWorkspaceFolder.exists())
                    {
                        ctx.getLogger().info("Deleting existing output folder: " + destinationWorkspaceFolder.getPath());
                        try
                        {
                            FileUtils.deleteDirectory(destinationWorkspaceFolder);
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

            if (ctx.getParams().optBoolean("disableFileLocking", false))
            {
                ctx.getLogger().debug("Disabling file locking for TileDB");
                wrapper.addToEnvironment("TILEDB_DISABLE_FILE_LOCKING", "1");
            }

            if (!genomicsDbCompleted)
            {
                try
                {
                    FileUtils.touch(startedFile);

                    List<Interval> intervals = getIntervals(ctx);

                    Integer maxRam = SequencePipelineService.get().getMaxRam();
                    Integer nativeMemoryBuffer = ctx.getParams().optInt("nativeMemoryBuffer", 0);
                    if (maxRam != null && nativeMemoryBuffer > 0)
                    {
                        ctx.getLogger().info("Adjusting RAM based on memory buffer (" + nativeMemoryBuffer + ")");
                        maxRam = maxRam - nativeMemoryBuffer;

                        if (maxRam < 1)
                        {
                            throw new PipelineJobException("After adjusting for nativeMemoryBuffer, maxRam is less than 1: " + maxRam);
                        }
                        wrapper.setMaxRamOverride(maxRam);
                    }

                    wrapper.execute(genome, vcfsToProcess, destinationWorkspaceFolder, intervals, options, _append);

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
                ctx.getLogger().info("Resuming from existing file: " + destinationWorkspaceFolder.getPath());
            }
            ctx.getFileManager().addIntermediateFile(doneFile);

            File markerFile = getMarkerFile(destinationWorkspaceFolder);
            if (!markerFile.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + markerFile.getPath());
            }

            ctx.getLogger().debug("adding sequence output: " + destinationWorkspaceFolder.getPath());
            SequenceOutputFile so1 = new SequenceOutputFile();
            so1.setName(destinationWorkspaceFolder.getName());

            int sampleCount = getSamplesForWorkspace(destinationWorkspaceFolder).size();

            if (_append)
            {
                int initialSampleCount = getSamplesForWorkspace(getSourceWorkspace(ctx.getParams(), ctx.getSequenceSupport())).size();

                so1.setDescription("GATK GenomicsDB, original samples: " + initialSampleCount + ", appended " + inputFiles.size() + " gVCFs, final samples: " + sampleCount + ".  GATK Version: " + wrapper.getVersionString());
            }
            else
            {
                so1.setDescription("GATK GenomicsDB, created from " + inputFiles.size() + " files, " + sampleCount + " samples.  GATK Version: " + wrapper.getVersionString());
            }

            so1.setFile(markerFile);
            so1.setLibrary_id(genomeId);
            so1.setCategory(CATEGORY);
            so1.setContainer(ctx.getJob().getContainerId());
            so1.setCreated(new Date());
            so1.setModified(new Date());
            ctx.addSequenceOutput(so1);
            action.addOutput(markerFile, "GenomicsDB Workspace", false);

            action.setEndTime(new Date());
            ctx.addActions(action);

            if (!toDelete.isEmpty())
            {
                ctx.getLogger().info("deleting locally copied gVCFs: " + toDelete.size());
                for (File f : toDelete)
                {
                    ctx.getLogger().debug(f.getPath());
                    f.delete();
                }
            }
        }
    }

    private boolean doCopyLocal(JSONObject params)
    {
        return params.optBoolean("doCopyGVcfLocal", false);
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

    @Override
    public void doWork(List<SequenceOutputFile> inputFiles, JobContext ctx) throws PipelineJobException
    {
        GenotypeGVCFHandler.doCopyGvcfLocally(inputFiles, ctx);
    }
}
