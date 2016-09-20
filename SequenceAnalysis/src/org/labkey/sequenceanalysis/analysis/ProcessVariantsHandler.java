package org.labkey.sequenceanalysis.analysis;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.Container;
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
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.io.IOException;
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
public class ProcessVariantsHandler implements SequenceOutputHandler, SequenceOutputHandler.HasActionNames
{
    private FileType _vcfFileType = new FileType(Arrays.asList(".vcf"), ".vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public ProcessVariantsHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Process Variants";
    }

    @Override
    public String getDescription()
    {
        return "Run one or more tools to process/filter VCF files";
    }

    @Override
    public String getButtonJSHandler()
    {
        return null;
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/sequenceanalysis/variantProcessing.view?outputFileIds=" + StringUtils.join(outputFileIds, ";"), c).getActionURL();
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
        return true;
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && _vcfFileType.isType(f.getFile());
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

    public static void initVariantProcessing(PipelineJob job, SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        List<PipelineStepProvider<VariantProcessingStep>> providers = SequencePipelineService.get().getSteps(job, VariantProcessingStep.class);
        for (PipelineStepProvider<VariantProcessingStep> provider : providers)
        {
            for (ToolParameterDescriptor pd : provider.getParameters())
            {
                if (pd instanceof ToolParameterDescriptor.CachableParam)
                {
                    Object val = pd.extractValue(job, provider, Object.class);
                    ((ToolParameterDescriptor.CachableParam)pd).doCache(job, val, support);
                }
            }
        }
    }

    public static File processVCF(File input, Integer libraryId, JobContext ctx) throws PipelineJobException
    {
        File currentVCF = input;

        ctx.getJob().getLogger().info("***Starting processing of file: " + input.getName());
        ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Processing: " + input.getName());

        List<PipelineStepProvider<VariantProcessingStep>> providers = SequencePipelineService.get().getSteps(ctx.getJob(), VariantProcessingStep.class);
        if (providers.isEmpty())
        {
            ctx.getLogger().info("no processing steps selected");
            return null;
        }

        for (PipelineStepProvider<VariantProcessingStep> provider : providers)
        {
            ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Running: " + provider.getLabel());

            RecordedAction action = new RecordedAction(provider.getLabel());
            Date start = new Date();
            action.setStartTime(start);
            action.addInput(currentVCF, "Input VCF");
            File vcfIdx = new File(currentVCF.getPath() + ".tbi");
            if (vcfIdx.exists())
            {
                action.addInput(vcfIdx, "Input VCF Index");
            }

            ctx.getFileManager().addIntermediateFile(currentVCF);
            ctx.getFileManager().addIntermediateFile(vcfIdx);

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(libraryId);
            action.addInput(genome.getSourceFastaFile(), "Reference FASTA");

            VariantProcessingStep step = provider.create(ctx);
            VariantProcessingStep.Output output = step.processVariants(currentVCF, ctx.getOutputDir(), genome);
            ctx.getFileManager().addStepOutputs(action, output);

            if (output.getVCF() != null)
            {
                currentVCF = output.getVCF();
            }
            else
            {
                throw new PipelineJobException("no output VCF produced");
            }

            ctx.getJob().getLogger().info("total variants: " + getVCFLineCount(currentVCF, ctx.getJob().getLogger(), false));
            ctx.getJob().getLogger().info("passing variants: " + getVCFLineCount(currentVCF, ctx.getJob().getLogger(), true));
            ctx.getJob().getLogger().debug("index exists: " + (new File(currentVCF.getPath() + ".tbi")).exists());

            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(currentVCF, ctx.getJob().getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            Date end = new Date();
            action.setEndTime(end);
            ctx.getJob().getLogger().info(provider.getLabel() + " Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
            ctx.addActions(action);
        }

        if (currentVCF.exists())
        {
            ctx.getFileManager().removeIntermediateFile(currentVCF);
            ctx.getFileManager().removeIntermediateFile(new File(currentVCF.getPath() + ".tbi"));

            return currentVCF;
        }

        return null;
    }

    private static String getVCFLineCount(File vcf, Logger log, boolean passOnly) throws PipelineJobException
    {
        String cat = vcf.getName().endsWith(".gz") ? "zcat" : "cat";
        SimpleScriptWrapper wrapper = new SimpleScriptWrapper(null);

        return wrapper.executeWithOutput(Arrays.asList("/bin/bash", "-c", cat + " \"" + vcf.getPath() + "\" | grep -v \"#\" | " + (passOnly ? "awk ' $7 == \"PASS\" ' | " : "") + "wc -l | awk \" { print $1 } \""));
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            initVariantProcessing(job, support);
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile input : inputFiles)
            {
                File processed = processVCF(input.getFile(), input.getLibrary_id(), ctx);
                if (processed != null && processed.exists())
                {
                    SequenceOutputFile so1 = new SequenceOutputFile();
                    so1.setName(processed.getName());
                    so1.setFile(processed);
                    so1.setLibrary_id(input.getLibrary_id());
                    so1.setCategory("VCF File");
                    so1.setContainer(ctx.getJob().getContainerId());
                    so1.setCreated(new Date());
                    so1.setModified(new Date());

                    ctx.addSequenceOutput(so1);
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }

    @Override
    public Collection<String> getAllowableActionNames()
    {
        Set<String> allowableNames = new HashSet<>();
        for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(VariantProcessingStep.class))
        {
            allowableNames.add(provider.getLabel());
        }

        return allowableNames;
    }
}
