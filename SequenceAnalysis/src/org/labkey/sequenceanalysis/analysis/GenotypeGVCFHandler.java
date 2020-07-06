package org.labkey.sequenceanalysis.analysis;

import htsjdk.samtools.util.Interval;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.StringUtils;
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
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.JobContextImpl;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;
import org.labkey.sequenceanalysis.run.util.CombineGVCFsWrapper;
import org.labkey.sequenceanalysis.run.util.GenomicsDBImportHandler;
import org.labkey.sequenceanalysis.run.util.GenotypeGVCFsWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler.VCF_CATEGORY;

/**
 * Created by bimber on 8/26/2014.
 */
public class GenotypeGVCFHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>, SequenceOutputHandler.HasActionNames, SequenceOutputHandler.TracksVCF, VariantProcessingStep.MayRequirePrepareTask
{
    private FileType _gvcfFileType = new FileType(Arrays.asList(".g.vcf"), ".g.vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

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
        for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(VariantProcessingStep.class))
        {
            allowableNames.add(provider.getLabel());
        }

        return allowableNames;
    }

    public GenotypeGVCFHandler()
    {
//                ToolParameterDescriptor.create("fileBaseName", "Filename", "This is the basename that will be used for the output gzipped VCF", "textfield", null, "CombinedGenotypes"),
//                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-stand_call_conf"), "stand_call_conf", "Threshold For Calling Variants", "The minimum phred-scaled confidence threshold at which variants should be called", "ldk-numberfield", null, 30),
//                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--max_alternate_alleles"), "max_alternate_alleles", "Max Alternate Alleles", "Maximum number of alternate alleles to genotype", "ldk-integerfield", null, 12),
//                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--includeNonVariantSites"), "includeNonVariantSites", "Include Non-Variant Sites", "If checked, all sites will be output into the VCF, instead of just those where variants are detected.  This can dramatically increase the size of the VCF.", "checkbox", null, false)
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
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            ProcessVariantsHandler.initVariantProcessing(job, support, inputFiles, outputDir);

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

            if (params.get("variantCalling.GenotypeGVCFs.forceSitesFile") != null)
            {
                int dataId = params.getInt("variantCalling.GenotypeGVCFs.forceSitesFile");
                ExpData data = ExperimentService.get().getExpData(dataId);
                if (data == null)
                {
                    throw new PipelineJobException("Unable to find ExpData with ID: " + dataId);
                }

                job.getLogger().debug("Caching ExpData: " + dataId);
                support.cacheExpData(data);
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            ProcessVariantsHandler.Resumer resumer = ProcessVariantsHandler.Resumer.create((JobContextImpl)ctx);

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
            File processed = ProcessVariantsHandler.processVCF(outputVcf, genomeId, ctx, resumer);
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

        private File runGenotypeGVCFs(PipelineJob job, JobContext ctx, ProcessVariantsHandler.Resumer resumer, List<File> inputFiles, int genomeId) throws PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            File outDir = ctx.getOutputDir();
            String basename = getBasename(ctx);

            File outputVcf = new File(outDir, basename + ".vcf.gz");

            for (File f : inputFiles)
            {
                action.addInput(f, "Input Variants");
            }

            boolean doCopyLocal = doCopyLocal(ctx.getParams());

            Set<File> toDelete = new HashSet<>();
            List<File> filesToProcess = new ArrayList<>();
            if (doCopyLocal)
            {
                ctx.getLogger().info("making local copies of gVCF/GenomicsDB files prior to genotyping");
                filesToProcess.addAll(GenotypeGVCFsWrapper.copyVcfsLocally(inputFiles, toDelete, GenotypeGVCFHandler.getLocalCopyDir(ctx, true), ctx.getLogger(), outputVcf.exists()));
            }
            else
            {
                filesToProcess.addAll(inputFiles);
            }

            //Allow CombineGVCFs to run on interval(s)
            File inputVcf;
            if (filesToProcess.size() > 1)
            {
                inputVcf = combineInputs(ctx, filesToProcess, genomeId);
                ctx.getFileManager().addIntermediateFile(inputVcf);
                ctx.getFileManager().addIntermediateFile(new File(inputVcf.getPath() + ".tbi"));
            }
            else
            {
                inputVcf = filesToProcess.get(0);
            }

            GenotypeGVCFsWrapper wrapper = new GenotypeGVCFsWrapper(job.getLogger());
            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(genomeId);
            if (genome == null)
            {
                throw new PipelineJobException("Unable to find cached genome for Id: " + genomeId);
            }

            List<String> toolParams = new ArrayList<>();
            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.stand_call_conf") != null)
            {
                toolParams.add("-stand-call-conf");
                toolParams.add(ctx.getParams().get("variantCalling.GenotypeGVCFs.stand_call_conf").toString());
            }

            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.max_alternate_alleles") != null)
            {
                toolParams.add("--max-alternate-alleles");
                toolParams.add(ctx.getParams().get("variantCalling.GenotypeGVCFs.max_alternate_alleles").toString());
            }

            if (ctx.getParams().optBoolean("variantCalling.GenotypeGVCFs.includeNonVariantSites"))
            {
                toolParams.add("--include-non-variant-sites");
            }

            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.forceSitesFile") != null)
            {
                File f = ctx.getSequenceSupport().getCachedData(ctx.getParams().getInt("variantCalling.GenotypeGVCFs.forceSitesFile"));
                toolParams.add("--force-output-intervals");
                toolParams.add(f.getPath());
            }

            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.allowOldRmsMappingData") != null)
            {
                toolParams.add("--allow-old-rms-mapping-quality-annotation-data");
            }

            List<Interval> intervals = ProcessVariantsHandler.getIntervals(ctx);
            if (intervals != null)
            {
                intervals.forEach(interval -> {
                    toolParams.add("-L");
                    toolParams.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                });
            }

            wrapper.execute(genome.getSourceFastaFile(), outputVcf, toolParams, inputVcf);

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

        private File combineInputs(JobContext ctx, List<File> inputFiles, int genomeId) throws PipelineJobException
        {
            for (File f : inputFiles)
            {
                if (!GenotypeGVCFsWrapper.GVCF.isType(f))
                {
                    throw new PipelineJobException("If multiple inputs are used, all must be gVCFs: " + f.getName());
                }
            }

            String basename = getBasename(ctx);
            File combined = new File(ctx.getOutputDir(), basename + ".combined.gvcf.gz");

            File idx = new File(combined.getPath() + ".tbi");
            if (idx.exists())
            {
                ctx.getLogger().info("Index exists, resuming combine with existing file: " + combined.getPath());
                return combined;
            }

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(genomeId);
            if (genome == null)
            {
                throw new PipelineJobException("Unable to find cached genome for Id: " + genomeId);
            }

            List<String> toolParams = new ArrayList<>();
            List<Interval> intervals = ProcessVariantsHandler.getIntervals(ctx);
            if (intervals != null)
            {
                intervals.forEach(interval -> {
                    toolParams.add("-L");
                    toolParams.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                });
            }

            CombineGVCFsWrapper wrapper = new CombineGVCFsWrapper(ctx.getLogger());
            wrapper.execute(genome.getWorkingFastaFile(), combined, toolParams, inputFiles.toArray(new File[inputFiles.size()]));

            return combined;
        }
    }

    private boolean doCopyLocal(JSONObject params)
    {
        return params.optBoolean("variantCalling.GenotypeGVCFs.doCopyInputs", false);
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
        doCopyGvcfLocally(inputFiles, ctx);
    }

    public static void doCopyGvcfLocally(List<SequenceOutputFile> inputFiles, JobContext ctx) throws PipelineJobException
    {
        VariantProcessingJob vpj = (VariantProcessingJob)ctx.getJob();
        List<File> inputVCFs = new ArrayList<>();
        inputFiles.forEach(f -> inputVCFs.add(f.getFile()));

        ctx.getLogger().info("making local copies of gVCFs");
        GenotypeGVCFsWrapper.copyVcfsLocally(inputVCFs, new ArrayList<>(), getLocalCopyDir(ctx, true), ctx.getLogger(), false);
    }

    public static File getLocalCopyDir(JobContext ctx, boolean createIfDoesntExist)
    {
        if (ctx.getJob() instanceof VariantProcessingJob)
        {
            return ((VariantProcessingJob)ctx.getJob()).getLocationForCachedInputs(ctx.getWorkDir(), createIfDoesntExist);
        }

        return ctx.getOutputDir();
    }
}
