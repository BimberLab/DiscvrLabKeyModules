package org.labkey.sequenceanalysis.analysis;

import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
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

/**
 * Created by bimber on 8/26/2014.
 */
public class GenotypeGVCFHandler implements SequenceOutputHandler, SequenceOutputHandler.HasActionNames
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
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && _gvcfFileType.isType(f.getFile());
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
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            ProcessVariantsHandler.initVariantProcessing(job, support, inputFiles, outputDir);
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
            so1.setCategory("VCF File");
            so1.setContainer(job.getContainerId());
            so1.setCreated(new Date());
            so1.setModified(new Date());

            resumer.addSequenceOutput(so1);
            //TODO: rename output?

            resumer.markComplete(ctx);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        private File runGenotypeGVCFs(PipelineJob job, JobContext ctx, ProcessVariantsHandler.Resumer resumer, List<File> inputVcfs, int genomeId) throws PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            for (File f : inputVcfs)
            {
                action.addInput(f, "Input gVCF");
            }

            GenotypeGVCFsWrapper wrapper = new GenotypeGVCFsWrapper(job.getLogger());
            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(genomeId);
            if (genome == null)
            {
                throw new PipelineJobException("Unable to find cached genome for Id: " + genomeId);
            }

            File outDir = ctx.getOutputDir();
            String basename = ctx.getParams().get("variantCalling.GenotypeGVCFs.fileBaseName") != null ? ctx.getParams().getString("variantCalling.GenotypeGVCFs.fileBaseName") : "CombinedGenotypes";
            basename = basename.replaceAll(".vcf.gz$", "");
            basename = basename.replaceAll(".vcf$", "");

            File outputVcf = new File(outDir, basename + ".vcf.gz");
            List<String> toolParams = new ArrayList<>();
            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.stand_call_conf") != null)
            {
                toolParams.add("-stand_call_conf");
                toolParams.add(ctx.getParams().get("variantCalling.GenotypeGVCFs.stand_call_conf").toString());
            }

            if (ctx.getParams().get("variantCalling.GenotypeGVCFs.max_alternate_alleles") != null)
            {
                toolParams.add("--max_alternate_alleles");
                toolParams.add(ctx.getParams().get("variantCalling.GenotypeGVCFs.max_alternate_alleles").toString());
            }

            if (ctx.getParams().optBoolean("variantCalling.GenotypeGVCFs.includeNonVariantSites"))
            {
                toolParams.add("--includeNonVariantSites");
            }

            toolParams.add("-A");
            toolParams.add("FractionInformativeReads");

            boolean doCopyInputs = ctx.getParams().optBoolean("variantCalling.GenotypeGVCFs.doCopyInputs", false);

            wrapper.execute(genome.getSourceFastaFile(), outputVcf, toolParams, doCopyInputs, inputVcfs.toArray(new File[inputVcfs.size()]));
            action.addOutput(outputVcf, "VCF", outputVcf.exists(), true);
            action.setEndTime(new Date());
            resumer.setGenotypeGVCFsComplete(action, outputVcf);

            return outputVcf;
        }
    }
}
