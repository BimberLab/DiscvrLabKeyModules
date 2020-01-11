package org.labkey.sequenceanalysis.run.util;

import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
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
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
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
import java.util.Set;

/**
 * Created by bimber on 4/2/2017.
 */
public class CombineGVCFsHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> implements SequenceOutputHandler.TracksVCF
{
    public static final String NAME = "Combine GVCFs";
    private static final String COMBINED_CATEGORY = "Combined gVCF File";

    private FileType _gvcfFileType = new FileType(Arrays.asList(".g.vcf"), ".g.vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public CombineGVCFsHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), NAME, "This will run GATK\'s CombineGVCFs on a set of GVCF files.  Note: this cannot work against any VCF file - these are primarily VCFs created using GATK\'s HaplotypeCaller.", null, Arrays.asList(
                ToolParameterDescriptor.create("fileBaseName", "Filename", "This is the basename that will be used for the output gzipped VCF", "textfield", null, "CombinedGenotypes"),
                ToolParameterDescriptor.create("doCopyLocal", "Copy gVCFs To Working Directory", "If selected, the gVCFs will be copied to the working directory first, which can improve performance when working with a large set of files.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create("scatterGather", "Process Chromosomes in Parallel", "If selected, this job will be divided to run job per chromosome.  The final step will take the VCF from each intermediate step and combined to make a final VCF file.", "checkbox", new JSONObject(){{
                    put("checked", false);
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
    public File getFinalVCF(JobContext ctx) throws PipelineJobException
    {
        return ProcessVariantsHandler.getVcfOutputByCategory(ctx, COMBINED_CATEGORY);
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

            //NOTE: because this is an especially long single-step task, build in local 'resume'
            boolean isResume = false;
            String basename = ctx.getParams().getString("fileBaseName");
            File outputFile = new File(ctx.getOutputDir(), basename + (basename.endsWith(".") ? "" : ".") + "g.vcf.gz");
            File outputFileIdx = new File(outputFile.getPath() + ".tbi");
            if (outputFileIdx.exists())
            {
                ctx.getLogger().info("expected output already exists, will attempt to resume using this output");
                isResume = true;
            }

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

            CombineGVCFsWrapper wrapper = new CombineGVCFsWrapper(ctx.getLogger());
            if (!isResume)
            {
                List<String> options = new ArrayList<>();
                if (ctx.getJob() instanceof VariantProcessingJob)
                {
                    VariantProcessingJob job = (VariantProcessingJob)ctx.getJob();
                    if (job.getContigForTask() != null)
                    {
                        options.add("-L");
                        options.add(job.getContigForTask());
                    }
                }

                wrapper.execute(genome.getWorkingFastaFile(), outputFile, options, vcfsToProcess.toArray(new File[vcfsToProcess.size()]));
            }

            if (!outputFile.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + outputFile.getPath());
            }

            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(outputFile, ctx.getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            ctx.getLogger().debug("adding sequence output: " + outputFile.getPath());
            SequenceOutputFile so1 = new SequenceOutputFile();
            so1.setName(outputFile.getName());

            if (ctx.getJob() instanceof VariantProcessingJob)
            {
                VariantProcessingJob job = (VariantProcessingJob) ctx.getJob();
                if (job.getContigForTask() != null)
                {
                    job.getFinalVCFs().put(job.getContigForTask(), outputFile);
                }
            }

            int sampleCount;
            try (VCFFileReader reader = new VCFFileReader(outputFile))
            {
                VCFHeader header = reader.getFileHeader();
                sampleCount = header.getSampleNamesInOrder().size();
            }

            so1.setDescription("GATK CombineGVCFs Output, created from " + inputFiles.size() + " files, " + sampleCount + " samples.  GATK Version: " + wrapper.getVersionString());
            so1.setFile(outputFile);
            so1.setLibrary_id(genomeId);
            so1.setCategory(COMBINED_CATEGORY);
            so1.setContainer(ctx.getJob().getContainerId());
            so1.setCreated(new Date());
            so1.setModified(new Date());
            ctx.addSequenceOutput(so1);
            action.addOutput(outputFile, "Combined gVCF", false);

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
}
