package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.util.Interval;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by bimber on 4/2/2017.
 */
public class GenomicsDBImportHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> implements SequenceOutputHandler.TracksVCF, SequenceOutputHandler.HasCustomVariantMerge
{
    public static final String NAME = "GenomicsDB Import";
    public static final String CATEGORY = "GenomicsDB Workspace";

    private FileType _gvcfFileType = new FileType(Arrays.asList(".g.vcf"), ".g.vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public GenomicsDBImportHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), NAME, "This will run GATK\'s GenomicsDBImport on a set of GVCF files.  Note: this cannot work against any VCF file - these are primarily VCFs created using GATK\'s HaplotypeCaller.", null, Arrays.asList(
                ToolParameterDescriptor.create("fileBaseName", "Filename", "This is the basename that will be used for the output gzipped VCF", "textfield", null, "CombinedGenotypes"),
                ToolParameterDescriptor.create("doCopyLocal", "Copy gVCFs To Working Directory", "If selected, the gVCFs will be copied to the working directory first, which can improve performance when working with a large set of files.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--batch-size"), "batchSize", "Batch Size", "Batch size controls the number of samples for which readers are open at once and therefore provides a way to minimize memory consumption. However, it can take longer to complete. Use the consolidate flag if more than a hundred batches were used. This will improve feature read time. batchSize=0 means no batching (i.e. readers for all samples will be opened at once) Defaults to 0.", "ldk-integerfield", null, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--reader-threads"), "readerThreads", "Reader Threads", "How many simultaneous threads to use when opening VCFs in batches; higher values may improve performance when network latency is an issue", "ldk-integerfield", null, null),
                ToolParameterDescriptor.create("scatterGather", "Scatter/Gather Options", "If selected, this job will be divided to run job per chromosome.  The final step will take the VCF from each intermediate step and combined to make a final VCF file.", "sequenceanalysis-variantscattergatherpanel", new JSONObject(){{
                    put("defaultValue", "chunked");
                }}, false)
        ));
    }

    @Override
    public File getScatterJobOutput(JobContext ctx) throws PipelineJobException
    {
        return ProcessVariantsHandler.getScatterOutputByCategory(ctx, GenomicsDBImportHandler.CATEGORY);
    }

    @Override
    public SequenceOutputFile createFinalSequenceOutput(PipelineJob job, File mergedWorkspace, List<SequenceOutputFile> inputFiles)
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

        AtomicInteger sampleCount = new AtomicInteger();
        for (SequenceOutputFile so : inputFiles)
        {
            try (VCFFileReader reader = new VCFFileReader(so.getFile()))
            {
                VCFHeader header = reader.getFileHeader();
                sampleCount.getAndAdd(header.getSampleNamesInOrder().size());
            }
        }

        SequenceOutputFile so1 = new SequenceOutputFile();
        so1.setName(getPipelineJob(job).getParameterJson().getString("basename"));
        so1.setFile(mergedWorkspace);
        so1.setLibrary_id(libraryIds.iterator().next());
        so1.setCategory(GenomicsDBImportHandler.CATEGORY);
        so1.setContainer(job.getContainerId());
        so1.setCreated(new Date());
        so1.setModified(new Date());
        so1.setReadset((readsetIds.size() != 1 ? null : readsetIds.iterator().next()));
        so1.setDescription("Total samples: " + sampleCount.get());

        return so1;
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
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            //TODO: add to existing?
            //See: https://gatk.broadinstitute.org/hc/en-us/articles/360035891051-GenomicsDB
            boolean doCopyLocal = ctx.getParams().optBoolean("doCopyLocal", false);

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

            File workspaceFolder = getWorkspaceOutput(ctx.getOutputDir(), ctx.getParams().getString("fileBaseName"));

            File doneFile = new File(workspaceFolder, "genomicsdb.done");
            boolean isResume = doneFile.exists();

            Set<File> toDelete = new HashSet<>();
            List<File> vcfsToProcess = new ArrayList<>();
            if (doCopyLocal)
            {
                ctx.getLogger().info("making local copies of gVCFs");
                vcfsToProcess.addAll(GenotypeGVCFsWrapper.copyVcfsLocally(inputVcfs, toDelete, null, ctx.getLogger(), isResume));
            }
            else
            {
                vcfsToProcess.addAll(inputVcfs);
            }

            GenomicsDbImportWrapper wrapper = new GenomicsDbImportWrapper(ctx.getLogger());
            List<String> options = new ArrayList<>(getClientCommandArgs(ctx.getParams()));

            if (!isResume)
            {
                List<Interval> intervals = getIntervals(ctx);
                wrapper.execute(genome, vcfsToProcess, workspaceFolder, intervals, options);

                try
                {
                    FileUtils.touch(doneFile);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                ctx.getLogger().info("Resuming from existing file: " + workspaceFolder.getPath());
            }
            ctx.getFileManager().addIntermediateFile(doneFile);

            File markerFile = getMarkerFile(workspaceFolder);
            if (!markerFile.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + markerFile.getPath());
            }

            ctx.getLogger().debug("adding sequence output: " + workspaceFolder.getPath());
            SequenceOutputFile so1 = new SequenceOutputFile();
            so1.setName(workspaceFolder.getName());

            int sampleCount = 0;
            for (File gVCF : inputVcfs)
            {
                try (VCFFileReader reader = new VCFFileReader(gVCF))
                {
                    VCFHeader header = reader.getFileHeader();
                    sampleCount += header.getSampleNamesInOrder().size();
                }
            }

            so1.setDescription("GATK GenomicsDB, created from " + inputFiles.size() + " files, " + sampleCount + " samples.  GATK Version: " + wrapper.getVersionString());
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
                ctx.getLogger().info("deleting locally copied gVCFs");
                for (File f : toDelete)
                {
                    f.delete();
                }
            }
        }
    }

    private File getWorkspaceOutput(File outDir, String basename)
    {
        basename = FileUtil.makeLegalName(basename);
        return new File(outDir, basename + (basename.endsWith(".") ? "" : ".") + "gdb");
    }

    private List<Interval> getIntervals(JobContext ctx)
    {
        if (ctx.getJob() instanceof VariantProcessingJob)
        {
            VariantProcessingJob job = (VariantProcessingJob) ctx.getJob();

            return job.getIntervalsForTask();
        }

        throw new IllegalArgumentException("This job requires intervals to be set");
    }

    private VariantProcessingJob getPipelineJob(PipelineJob job)
    {
        return (VariantProcessingJob) job;
    }

    @Override
    public File performVariantMerge(TaskFileManager manager, RecordedAction action, SequenceOutputHandler<SequenceOutputProcessor> handler, PipelineJob job) throws PipelineJobException
    {
        Map<String, List<Interval>> jobToIntervalMap = getPipelineJob(job).getJobToIntervalMap();
        job.setStatus(PipelineJob.TaskStatus.running, "Creating merged workspace from: " + jobToIntervalMap.size());

        VariantProcessingJob variantProcessingJob = getPipelineJob(job);
        File destinationWorkspace = getWorkspaceOutput(variantProcessingJob.getDataDirectory(), variantProcessingJob.getParameterJson().getString("basename"));
        if (!destinationWorkspace.exists())
        {
            destinationWorkspace.mkdirs();
        }

        Map<String, File> scatterOutputs = getPipelineJob(job).getScatterJobOutputs();
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
                boolean copiedTopLevelFiles = false;

                Set<File> toDelete = new HashSet<>();
                for (Interval i : jobToIntervalMap.get(name))
                {
                    job.getLogger().info("Copying contig folder for job: " + name);
                    File copyDone = new File(destinationWorkspace, name + ".copy.done");
                    toDelete.add(copyDone);

                    if (copyDone.exists())
                    {
                        job.getLogger().info("has been copied, skipping");
                    }

                    if (!copiedTopLevelFiles)
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

                        copiedTopLevelFiles = true;
                    }

                    File expectedFolder = new File(sourceWorkspace, getFolderNameFromInterval(i));
                    if (!expectedFolder.exists())
                    {
                        throw new PipelineJobException("Unable to find expected file: " + expectedFolder.getPath());
                    }

                    File destContigFolder = new File(destinationWorkspace, expectedFolder.getName());
                    if (destContigFolder.exists())
                    {
                        job.getLogger().info("Target exists, deleting: " + destContigFolder.getPath());
                        FileUtils.deleteDirectory(destContigFolder);
                    }

                    FileUtils.moveDirectory(expectedFolder, destContigFolder);
                    FileUtils.touch(copyDone);
                }

                toDelete.forEach(File::delete);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return getMarkerFile(destinationWorkspace);
    }

    private File getMarkerFile(File workspace)
    {
        return new File(workspace, "__tiledb_workspace.tdb");
    }

    public static String getFolderNameFromInterval(Interval i)
    {
        return i.getContig() + "$" + i.getStart() + "$" + i.getEnd();
    }
}
