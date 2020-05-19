package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.util.Interval;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.GenomicsDBCollection;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;

import java.io.File;
import java.io.IOException;
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


/**
 * Created by bimber on 4/2/2017.
 */
public class CreateGenomicsDbCollectionHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> implements SequenceOutputHandler.TracksVCF, SequenceOutputHandler.HasCustomVariantMerge
{
    public static final String NAME = "Create GenomicsDB Collection";
    private static final String CATEGORY = GenomicsDBCollection.NAME;

    private FileType _gvcfFileType = new FileType(Arrays.asList(".g.vcf"), ".g.vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public CreateGenomicsDbCollectionHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), NAME, "This will run GATK\'s GenomicsDBImport on a set of GVCF files, creating a folder of workspaces, with one per set of interval(s), as specified.  This is designed to facilitate future scatter/gather processing.  Note: this cannot work against any VCF file - these are primarily VCFs created using GATK\'s HaplotypeCaller. ", new LinkedHashSet<>(Arrays.asList("sequenceanalysis/panel/VariantScatterGatherPanel.js")), Arrays.asList(
                ToolParameterDescriptor.create("fileBaseName", "Filename", "This is the basename that will be used for the output workspaces", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("doCopyGVCFsLocal", "Copy gVCFs To Working Directory", "If selected, the gVCFs will be copied to the working directory first, which can improve performance when working with a large set of files, but be careful when using with scatter/gather since each job operates independently.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("scatterGather", "Scatter/Gather Options", "If selected, this job will be divided to run job per chromosome.  The final step will take the VCF from each intermediate step and combined to make a final VCF file.", "sequenceanalysis-variantscattergatherpanel", new JSONObject(){{
                    put("defaultValue", "chunked");
                }}, false)
        ));
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

    @Override
    public File getScatterJobOutput(JobContext ctx) throws PipelineJobException
    {
        return ProcessVariantsHandler.getScatterOutputByCategory(ctx, GenomicsDBImportHandler.CATEGORY);
    }

    @Override
    public SequenceOutputFile createFinalSequenceOutput(PipelineJob job, File processed, List<SequenceOutputFile> inputFiles)
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

        int sampleCount;
        try (VCFFileReader reader = new VCFFileReader(processed))
        {
            VCFHeader header = reader.getFileHeader();
            sampleCount = header.getSampleNamesInOrder().size();
        }

        SequenceOutputFile so1 = new SequenceOutputFile();
        so1.setName(processed.getName());
        so1.setFile(processed);
        so1.setLibrary_id(libraryIds.iterator().next());
        so1.setCategory(GenomicsDBCollection.NAME);
        so1.setContainer(job.getContainerId());
        so1.setCreated(new Date());
        so1.setModified(new Date());
        so1.setReadset((readsetIds.isEmpty() ? null : readsetIds.iterator().next()));
        so1.setDescription("Total samples: " + sampleCount);

        return so1;
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
            boolean doCopyGVCFsLocal = ctx.getParams().optBoolean("doCopyGVCFsLocal", false);

            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            Set<Integer> genomeIds = new HashSet<>();
            List<File> inputVcfs = new ArrayList<>();
            Set<String> samples = new HashSet<>();
            for (SequenceOutputFile so : inputFiles)
            {
                genomeIds.add(so.getLibrary_id());
                inputVcfs.add(so.getFile());
                action.addInput(so.getFile(), "Input gVCF");

                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    VCFHeader header = reader.getFileHeader();

                    if (CollectionUtils.containsAny(samples, header.getGenotypeSamples()))
                    {
                        Set<String> duplicates = new HashSet<>(header.getGenotypeSamples());
                        duplicates.retainAll(samples);

                        throw new PipelineJobException("Duplicate samples across inputs.  VCF: " + so.getFile().getName() + ", samples: " + StringUtils.join(duplicates, ","));
                    }

                    samples.addAll(header.getGenotypeSamples());
                }
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

            //NOTE: because this is an especially long single-step task, build in local 'resume'
            boolean isResume = false;
            String basename = ctx.getParams().getString("fileBaseName");
            File outputFile = new File(ctx.getOutputDir(), basename + (basename.endsWith(".") ? "" : ".") + "genomicsdb");

            //TODO: determine what file only exists on completion
            //File jsonFile = new File(outputFile, "vidmap.json");
            //if (jsonFile.exists())
            //{
            //    ctx.getLogger().info("Expected workspace already exists, will attempt to resume using this output");
            //    isResume = true;
            //}

            Set<File> toDelete = new HashSet<>();
            List<File> vcfsToProcess = new ArrayList<>();
            if (doCopyGVCFsLocal)
            {
                ctx.getLogger().info("making local copies of gVCFs");
                vcfsToProcess.addAll(GenotypeGVCFsWrapper.copyVcfsLocally(inputVcfs, toDelete, null, ctx.getLogger(), isResume));
            }
            else
            {
                vcfsToProcess.addAll(inputVcfs);
            }

            GenomicsDbImportWrapper wrapper = new GenomicsDbImportWrapper(ctx.getLogger());
            if (!isResume)
            {
                List<Interval> intervals = getIntervals(ctx);
                wrapper.execute(genome, vcfsToProcess, outputFile, intervals, null);
            }

            if (!outputFile.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + outputFile.getPath());
            }

            ctx.getLogger().debug("adding sequence output: " + outputFile.getPath());
            SequenceOutputFile so1 = new SequenceOutputFile();
            so1.setName(outputFile.getName());

            List<Interval> intervals = getIntervals(ctx);
            VariantProcessingJob job = (VariantProcessingJob) ctx.getJob();
            if (job.getIntervalsForTask() != null)
            {
                //NOTE: the folder was copied back to the source dir, so translate paths
                try
                {
                    String path = ctx.getWorkDir().getRelativePath(outputFile);
                    File movedOutputFile = new File(ctx.getSourceDirectory(), path);
                    job.getScatterJobOutputs().put(job.getIntervalSetName(), movedOutputFile);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            so1.setDescription("GenomicsDB Workspace, created from " + inputFiles.size() + " files, " + samples.size() + " samples.  GATK Version: " + wrapper.getVersionString());
            so1.setFile(outputFile);
            so1.setLibrary_id(genomeId);
            so1.setCategory(CATEGORY);
            so1.setContainer(ctx.getJob().getContainerId());
            so1.setCreated(new Date());
            so1.setModified(new Date());
            ctx.addSequenceOutput(so1);
            action.addOutput(outputFile, GenomicsDBCollection.NAME, false);

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
        job.setStatus(PipelineJob.TaskStatus.running, "Creating JSON file: " + jobToIntervalMap.size());

        File json = new File(getPipelineJob(job).getAnalysisDirectory(), "out.json");

        Map<String, File> scatterOutputs = getPipelineJob(job).getScatterJobOutputs();
        for (String name : jobToIntervalMap.keySet())
        {
            if (!scatterOutputs.containsKey(name))
            {
                throw new PipelineJobException("Missing output for interval/contig: " + name);
            }

            File vcf = scatterOutputs.get(name);
            if (!vcf.exists())
            {
                throw new PipelineJobException("Missing output: " + vcf.getPath());
            }

            //TODO: make JSON
        }

        return json;
    }
}
