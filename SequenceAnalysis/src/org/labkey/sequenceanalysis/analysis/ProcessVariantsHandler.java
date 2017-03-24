package org.labkey.sequenceanalysis.analysis;

import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureReader;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.PedigreeRecord;
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
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
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

    public static void initVariantProcessing(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, File outputDir) throws PipelineJobException
    {
        List<PipelineStepProvider<VariantProcessingStep>> providers = SequencePipelineService.get().getSteps(job, VariantProcessingStep.class);
        boolean requiresPedigree = false;
        for (PipelineStepProvider<VariantProcessingStep> provider : providers)
        {
            for (ToolParameterDescriptor pd : provider.getParameters())
            {
                if (pd instanceof ToolParameterDescriptor.CachableParam)
                {
                    job.getLogger().debug("caching params for : " + pd.getName());
                    Object val = pd.extractValue(job, provider, Object.class);
                    ((ToolParameterDescriptor.CachableParam)pd).doCache(job, val, support);
                }
            }

            if (provider instanceof VariantProcessingStep.RequiresPedigree)
            {
                requiresPedigree = true;
            }
        }

        if (requiresPedigree)
        {
            job.getLogger().info("writing pedigree files");
            Set<String> sampleNames = new CaseInsensitiveHashSet();
            for (SequenceOutputFile so : inputFiles)
            {
                job.getLogger().info("reading file: " + so.getFile().getName());
                try (FeatureReader reader = AbstractFeatureReader.getFeatureReader(so.getFile().getPath(), new VCFCodec(), false))
                {
                    VCFHeader header = (VCFHeader)reader.getHeader();
                    sampleNames.addAll(header.getSampleNamesInOrder());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            job.getLogger().info("total samples: " + sampleNames.size());
            List<PedigreeRecord> pedigreeRecords = SequenceAnalysisService.get().generatePedigree(sampleNames, job.getContainer(), job.getUser());
            job.getLogger().info("total pedigree records: " + pedigreeRecords.size());

            File pedFile = getPedigreeFile(outputDir);
            try (PrintWriter gatkWriter = PrintWriters.getPrintWriter(pedFile))
            {
                for (PedigreeRecord pd : pedigreeRecords)
                {
                    List<String> vals = Arrays.asList(pd.getSubjectName(), (StringUtils.isEmpty(pd.getFather()) ? "0" : pd.getFather()), (StringUtils.isEmpty(pd.getMother()) ? "0" : pd.getMother()), ("m".equals(pd.getGender()) ? "1" : "f".equals(pd.getGender()) ? "2" : "0"), "0");
                    gatkWriter.write("FAM01 " + StringUtils.join(vals, " ") + '\n');
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            job.getLogger().debug("pedigree file not required");
        }
    }

    public static File getPedigreeFile(File outputDir)
    {
        return new File(outputDir, "gatk.ped");
    }

    public static File processVCF(File input, Integer libraryId, JobContext ctx) throws PipelineJobException
    {
        try
        {
            SequenceAnalysisService.get().ensureVcfIndex(input, ctx.getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

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

                ctx.getJob().getLogger().info("total variants: " + getVCFLineCount(currentVCF, ctx.getJob().getLogger(), false));
                ctx.getJob().getLogger().info("passing variants: " + getVCFLineCount(currentVCF, ctx.getJob().getLogger(), true));
                ctx.getJob().getLogger().debug("index exists: " + (new File(currentVCF.getPath() + ".tbi")).exists());
            }
            else
            {
                ctx.getLogger().info("no output VCF produced");
            }

            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(currentVCF, ctx.getJob().getLogger(), true);
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

        ctx.getLogger().debug("no VCF produced at end of processing");

        return null;
    }

    private static String getVCFLineCount(File vcf, Logger log, boolean passOnly) throws PipelineJobException
    {
        String cat = vcf.getName().endsWith(".gz") ? "zcat" : "cat";
        SimpleScriptWrapper wrapper = new SimpleScriptWrapper(null);

        String ret = wrapper.executeWithOutput(Arrays.asList("/bin/bash", "-c", cat + " \"" + vcf.getPath() + "\" | grep -v \"#\" | " + (passOnly ? "awk ' $7 == \"PASS\" || $7 == \"\\.\" ' | " : "") + "wc -l | awk \" { print $1 } \""));

        //NOTE: unsure how to get awk to omit this warning, so discard it:
        if (ret != null)
        {
            String[] tokens = ret.split("\n");
            if (tokens.length > 1)
            {
                tokens = ArrayUtils.remove(tokens, 0);
                ret = StringUtils.join(tokens, "\n");
            }
        }

        return ret;
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            initVariantProcessing(job, support, inputFiles, outputDir);
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile input : inputFiles)
            {
                File processed = processVCF(input.getFile(), input.getLibrary_id(), ctx);
                if (processed != null && processed.exists())
                {
                    ctx.getLogger().debug("adding sequence output: " + processed.getPath());
                    if (input.getFile().equals(processed))
                    {
                        ctx.getLogger().debug("processed file equals input, skipping: " + processed.getPath());
                    }
                    else
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
