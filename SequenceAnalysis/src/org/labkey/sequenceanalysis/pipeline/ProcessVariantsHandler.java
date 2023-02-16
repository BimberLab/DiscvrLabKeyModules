package org.labkey.sequenceanalysis.pipeline;

import htsjdk.samtools.util.Interval;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureReader;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
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
import org.labkey.api.sequenceanalysis.pipeline.AbstractResumer;
import org.labkey.api.sequenceanalysis.pipeline.BcftoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
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
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.AbstractGenomicsDBImportHandler;
import org.labkey.sequenceanalysis.run.util.MergeVcfsAndGenotypesWrapper;
import org.labkey.sequenceanalysis.run.variant.OutputVariantsStartingInIntervalsStep;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by bimber on 8/26/2014.
 */
public class ProcessVariantsHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>, SequenceOutputHandler.HasActionNames, SequenceOutputHandler.TracksVCF, VariantProcessingStep.SupportsScatterGather
{
    public static final String VCF_CATEGORY = "VCF File";

    private final FileType _vcfFileType = new FileType(List.of(".vcf"), ".vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);
    private ProcessVariantsHandler.Resumer _resumer;

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
    public File getScatterJobOutput(JobContext ctx) throws PipelineJobException
    {
        return getScatterOutputByCategory(ctx, VCF_CATEGORY);
    }

    @Override
    public SequenceOutputFile createFinalSequenceOutput(PipelineJob job, File processed, List<SequenceOutputFile> inputFiles)
    {
        return createSequenceOutput(job, processed, inputFiles, VCF_CATEGORY);
    }

    public static SequenceOutputFile createSequenceOutput(PipelineJob job, File processed, List<SequenceOutputFile> inputFiles, String category)
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
        so1.setCategory(category);
        so1.setContainer(job.getContainerId());
        so1.setCreated(new Date());
        so1.setModified(new Date());
        so1.setReadset((readsetIds.size() != 1 ? null : readsetIds.iterator().next()));
        so1.setDescription("Total samples: " + sampleCount);

        return so1;
    }

    public static File getScatterOutputByCategory(JobContext ctx, final String category) throws PipelineJobException
    {
        Set<File> scatterOutputs = new HashSet<>();
        TaskFileManagerImpl manager = (TaskFileManagerImpl)ctx.getFileManager();
        ctx.getLogger().debug("Inspecting " + manager.getOutputsToCreate().size() + " outputs for category: " + category);
        manager.getOutputsToCreate().forEach(x ->  {
            if (category.equals(x.getCategory()))
            {
                scatterOutputs.add(x.getFile());
            }
        });

        if (scatterOutputs.isEmpty())
        {
            throw new PipelineJobException("Unable to find final VCF");
        }
        else if (scatterOutputs.size() > 1)
        {
            throw new PipelineJobException("More than one output tagged as final VCF");
        }

        return scatterOutputs.iterator().next();
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    private static SequenceOutputHandlerJob getPipelineJob(PipelineJob job)
    {
        return (SequenceOutputHandlerJob)job;
    }

    private static VariantProcessingJob getVariantPipelineJob(PipelineJob job)
    {
        return job instanceof VariantProcessingJob ? (VariantProcessingJob)job : null;
    }

    public static void initVariantProcessing(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, File outputDir) throws PipelineJobException
    {
        SequenceTaskHelper taskHelper = new SequenceTaskHelper(getPipelineJob(job), outputDir);

        List<PipelineStepCtx<VariantProcessingStep>> providers = SequencePipelineService.get().getSteps(job, VariantProcessingStep.class);
        boolean requiresPedigree = false;
        VariantProcessingJob vj = getVariantPipelineJob(job);
        boolean useScatterGather = vj != null && vj.isScatterJob();
        Set<String> stepsNotScatterGather = new HashSet<>();

        for (PipelineStepCtx<VariantProcessingStep> stepCtx : providers)
        {
            for (ToolParameterDescriptor pd : stepCtx.getProvider().getParameters())
            {
                if (pd instanceof ToolParameterDescriptor.CachableParam)
                {
                    job.getLogger().debug("caching params for : " + pd.getName());
                    Object val = pd.extractValue(job, stepCtx.getProvider(), stepCtx.getStepIdx(), Object.class);
                    ((ToolParameterDescriptor.CachableParam)pd).doCache(job, val, support);
                }
            }

            if (stepCtx.getProvider() instanceof VariantProcessingStep.RequiresPedigree)
            {
                requiresPedigree = true;
            }

            if (useScatterGather)
            {
                if (!(stepCtx.getProvider() instanceof VariantProcessingStep.SupportsScatterGather))
                {
                    stepsNotScatterGather.add(stepCtx.getProvider().getName());
                }

                ((VariantProcessingStep.SupportsScatterGather)stepCtx.getProvider()).validateScatter(vj.getScatterGatherMethod(), job);
            }

            VariantProcessingStep step = stepCtx.getProvider().create(taskHelper);
            step.init(job, support, inputFiles);
        }

        if (!stepsNotScatterGather.isEmpty())
        {
            throw new PipelineJobException("The follow steps do not support scatter/gather: " + StringUtils.join(stepsNotScatterGather, ", "));
        }

        if (requiresPedigree)
        {
            job.getLogger().info("writing pedigree files");
            Set<String> sampleNames = new CaseInsensitiveHashSet();
            for (SequenceOutputFile so : inputFiles)
            {
                sampleNames.addAll(getSamples(so.getFile()));
            }

            job.getLogger().info("total samples: " + sampleNames.size());
            job.getLogger().debug(StringUtils.join(sampleNames, ";"));
            List<PedigreeRecord> pedigreeRecords = SequenceAnalysisService.get().generatePedigree(sampleNames, job.getContainer(), job.getUser());
            job.getLogger().info("total pedigree records: " + pedigreeRecords.size());

            File pedFile = getPedigreeFile(outputDir);
            Set<String> sampleNamesCopy = new HashSet<>(sampleNames);
            Map<String, Integer> parentMap = new TreeMap<>();
            try (PrintWriter gatkWriter = PrintWriters.getPrintWriter(pedFile))
            {
                for (PedigreeRecord pd : pedigreeRecords)
                {
                    List<String> vals = Arrays.asList(pd.getSubjectName(), (StringUtils.isEmpty(pd.getFather()) ? "0" : pd.getFather()), (StringUtils.isEmpty(pd.getMother()) ? "0" : pd.getMother()), ("m".equals(pd.getGender()) ? "1" : "f".equals(pd.getGender()) ? "2" : "0"), "0");
                    gatkWriter.write("FAM01 " + StringUtils.join(vals, " ") + '\n');
                    sampleNamesCopy.remove(pd.getSubjectName());

                    String parentKey = pd.getTotalParents(false) + "/" + pd.getTotalParents(true);
                    if (!parentMap.containsKey(parentKey))
                        parentMap.put(parentKey, 0);

                    parentMap.put(parentKey, parentMap.get(parentKey) + 1);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (!sampleNamesCopy.isEmpty())
            {
                job.getLogger().warn("the following " + sampleNamesCopy.size() + " animals do not have pedigree records: ");
                for (String sn : sampleNamesCopy)
                {
                    job.getLogger().warn(sn);
                }
            }

            for (String key : parentMap.keySet())
            {
                job.getLogger().info("records with " + key + " (known / total including placeholder) parents: " + parentMap.get(key));
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

    public static List<Interval> getIntervals(JobContext ctx)
    {
        PipelineJob pj = ctx.getJob();
        if (pj instanceof VariantProcessingJob vpj)
        {
            if (vpj.isScatterJob())
            {
                List<Interval> intervals = vpj.getIntervalsForTask();
                ctx.getLogger().debug("This job will process " + intervals.size() + " intervals: " + vpj.getIntervalSetName());

                return intervals;
            }
        }

        return null;
    }

    public static File processVCF(File input, Integer libraryId, JobContext ctx, Resumer resumer, boolean subsetToIntervals) throws PipelineJobException
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
        int stepIdx = 0;
        List<PipelineStepCtx<VariantProcessingStep>> providers = SequencePipelineService.get().getSteps(ctx.getJob(), VariantProcessingStep.class);
        if (providers.isEmpty())
        {
            ctx.getLogger().info("no processing steps selected");
            return null;
        }

        boolean useScatterGather = getVariantPipelineJob(ctx.getJob()) != null && getVariantPipelineJob(ctx.getJob()).isScatterJob();
        if (useScatterGather && subsetToIntervals)
        {
            if (getIntervals(ctx) == null)
            {
                throw new PipelineJobException("Did not expect intervals to be null on a scatter/gather job");
            }

            ctx.getLogger().info("Subsetting input VCF to job intervals");
            ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Subsetting input VCF to job intervals");

            File outputFile = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".subset.vcf.gz");
            File outputFileIdx = new File(outputFile.getPath() + ".tbi");
            if (outputFileIdx.exists())
            {
                ctx.getLogger().debug("Index exists, will not re-subset VCF");
            }
            else
            {
                OutputVariantsStartingInIntervalsStep.Wrapper wrapper = new OutputVariantsStartingInIntervalsStep.Wrapper(ctx.getLogger());
                wrapper.execute(input, outputFile, getIntervals(ctx));
            }

            currentVCF = outputFile;
            resumer.getFileManager().addIntermediateFile(currentVCF);
            resumer.getFileManager().addIntermediateFile(outputFileIdx);
        }

        for (PipelineStepCtx<VariantProcessingStep> stepCtx : providers)
        {
            ctx.getLogger().info("Starting to run: " + stepCtx.getProvider().getLabel());
            ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Running: " + stepCtx.getProvider().getLabel());
            stepIdx++;

            if (resumer.isStepComplete(stepIdx, input.getPath()))
            {
                ctx.getLogger().info("resuming from saved state");
                currentVCF = resumer.getVcfFromStep(stepIdx, input.getPath());
                continue;
            }

            RecordedAction action = new RecordedAction(stepCtx.getProvider().getLabel());
            Date start = new Date();
            action.setStartTime(start);
            action.addInput(currentVCF, "Input VCF");
            File vcfIdx = new File(currentVCF.getPath() + ".tbi");
            if (vcfIdx.exists())
            {
                action.addInput(vcfIdx, "Input VCF Index");
            }

            if (!URIUtil.isDescendant(ctx.getOutputDir().toURI(), currentVCF.toURI()))
            {
                ctx.getLogger().info("VCF is not a descendent of the output directory, will not add as intermediate file: " + currentVCF.getPath());
            }
            else
            {
                resumer.getFileManager().addIntermediateFile(currentVCF);
                resumer.getFileManager().addIntermediateFile(vcfIdx);
            }

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(libraryId);
            action.addInput(genome.getSourceFastaFile(), "Reference FASTA");

            VariantProcessingStep step = stepCtx.getProvider().create(ctx);
            step.setStepIdx(stepCtx.getStepIdx());

            List<Interval> intervals = getIntervals(ctx);

            VariantProcessingStep.Output output = step.processVariants(currentVCF, ctx.getOutputDir(), genome, intervals);
            resumer.getFileManager().addStepOutputs(action, output);

            if (output.getVCF() != null)
            {
                currentVCF = output.getVCF();

                ctx.getJob().getLogger().info("total variants: " + getVCFLineCount(currentVCF, ctx.getJob().getLogger(), false, true));
                ctx.getJob().getLogger().info("passing variants: " + getVCFLineCount(currentVCF, ctx.getJob().getLogger(), true, false));
                ctx.getJob().getLogger().debug("index exists: " + (new File(currentVCF.getPath() + ".tbi")).exists());

                try
                {
                    SequenceAnalysisService.get().ensureVcfIndex(currentVCF, ctx.getJob().getLogger(), true);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                ctx.getLogger().info("no output VCF produced");
            }

            Date end = new Date();
            action.setEndTime(end);
            ctx.getJob().getLogger().info(stepCtx.getProvider().getLabel() + " Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));

            resumer.setStepComplete(stepIdx, input.getPath(), action, currentVCF);
        }

        if (currentVCF.exists())
        {
            resumer.getFileManager().removeIntermediateFile(currentVCF);
            resumer.getFileManager().removeIntermediateFile(new File(currentVCF.getPath() + ".tbi"));

            return currentVCF;
        }

        ctx.getLogger().debug("no VCF produced at end of processing");

        return null;
    }

    private static String countUsingBcfTools(File vcf, Logger log) throws PipelineJobException
    {
        BcftoolsRunner wrapper = new BcftoolsRunner(log);

        return wrapper.executeWithOutput(Arrays.asList(wrapper.getBcfToolsPath().getPath(), "index", "-n", vcf.getPath()));
    }

    public static String getVCFLineCount(File vcf, Logger log, boolean passOnly, boolean useBcfTools) throws PipelineJobException
    {
        if (useBcfTools)
        {
            if (passOnly)
            {
                throw new PipelineJobException("bcftools VCF count cannot be used with passOnly");
            }

            try
            {
                return countUsingBcfTools(vcf, log);
            }
            catch (PipelineJobException e)
            {
                // ignore and continue:
                log.warn("Cannot count variants using bcftools, this index might have been created using another tool");
            }

        }

        String cat = vcf.getName().endsWith(".gz") ? "zcat" : "cat";
        SimpleScriptWrapper wrapper = new SimpleScriptWrapper(null);

        String ret = wrapper.executeWithOutput(Arrays.asList("/bin/bash", "-c", cat + " \"" + vcf.getPath() + "\" | grep -v \"#\" | " + (passOnly ? "awk ' $7 == \"PASS\" || $7 == \"\\.\" ' | " : "") + "wc -l | awk \" { print $1 } \""));

        //NOTE: unsure how to get awk to omit this warning, so discard it:
        //the warning is: escape '\.' treated as plain '.'
        if (ret != null)
        {
            ret = ret.trim();
            String[] tokens = ret.split("\n");
            if (tokens.length > 1)
            {
                tokens = ArrayUtils.remove(tokens, 0);
                ret = StringUtils.join(tokens, "\n");
                ret = ret.trim();
            }
        }

        return ret;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            initVariantProcessing(ctx.getJob(), ctx.getSequenceSupport(), inputFiles, ctx.getOutputDir());
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            _resumer = Resumer.create(ctx);

            boolean doCombine = ctx.getParams().optBoolean("variantMerging.CombineVCFs.doCombine", false);
            if (doCombine)
            {
                ctx.getLogger().info("Input VCFs will be combined");
                String priorityOrder = StringUtils.trimToNull(ctx.getParams().optString("variantMerging.CombineVCFs.priority"));
                if (priorityOrder == null)
                {
                    throw new PipelineJobException("Priority order not supplied for VCFs");
                }

                Set<Integer> genomes = new HashSet<>();
                inputFiles.forEach(x -> genomes.add(x.getLibrary_id()));

                if (genomes.size() != 1)
                {
                    throw new PipelineJobException("More than one reference genome found!");
                }

                ReferenceGenome rg = ctx.getSequenceSupport().getCachedGenome(genomes.iterator().next());
                MergeVcfsAndGenotypesWrapper cv = new MergeVcfsAndGenotypesWrapper(ctx.getLogger());

                Map<Integer, Integer> fileMap = new HashMap<>();
                inputFiles.forEach(x -> fileMap.put(x.getRowid(), x.getDataId()));
                String[] ids = priorityOrder.split(",");

                List<File> vcfsInPriority = new ArrayList<>();
                for (String id : ids)
                {
                    int i = Integer.parseInt(id);
                    if (!fileMap.containsKey(i))
                    {
                        throw new PipelineJobException("Unable to find file matching priority: " + i);
                    }

                    int dataId = fileMap.get(i);

                    vcfsInPriority.add(ctx.getSequenceSupport().getCachedData(dataId));
                }

                String basename = StringUtils.trimToNull(ctx.getParams().optString("variantMerging.CombineVCFs.fileBaseName"));
                if (basename == null)
                {
                    throw new PipelineJobException("Basename not supplied for VCFs");
                }

                File outFile = new File(ctx.getOutputDir(), basename + ".vcf.gz");
                File outFileIdx = new File(outFile.getPath() + ".tbi");
                if (outFileIdx.exists())
                {
                    ctx.getLogger().info("Combined VCF exists, will not re-create: " + outFile.getPath());
                }
                else
                {
                    List<String> args = new ArrayList<>();
                    args.add("-genotypeMergeOption");
                    args.add("PRIORITIZE");

                    List<Interval> intervals = getIntervals(ctx);
                    if (intervals != null)
                    {
                        for (Interval interval : intervals)
                        {
                            args.add("-L");
                            args.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                        }
                    }

                    cv.execute(rg.getWorkingFastaFile(), vcfsInPriority, outFile, args, true);
                }

                if (!outFile.exists())
                {
                    throw new PipelineJobException("Unable to find combined VCF: " + outFile.getPath());
                }

                processFile(outFile, rg.getGenomeId(), null, ctx);
            }
            else
            {
                for (SequenceOutputFile input : inputFiles)
                {
                    processFile(input.getFile(), input.getLibrary_id(), input.getReadset(), ctx);
                }
            }

            _resumer.markComplete(ctx);
        }

        private void processFile(File input, Integer libraryId, Integer readsetId, JobContext ctx) throws PipelineJobException
        {
            File processed = processVCF(input, libraryId, ctx, _resumer, true);
            if (processed != null && processed.exists())
            {
                ctx.getLogger().debug("adding sequence output: " + processed.getPath());
                if (input.equals(processed))
                {
                    ctx.getLogger().debug("processed file equals input, skipping: " + processed.getPath());
                }
                else
                {
                    int sampleCount;
                    try (VCFFileReader reader = new VCFFileReader(processed))
                    {
                        VCFHeader header = reader.getFileHeader();
                        sampleCount = header.getSampleNamesInOrder().size();
                    }

                    SequenceOutputFile so1 = new SequenceOutputFile();
                    so1.setName(processed.getName());
                    so1.setFile(processed);
                    so1.setLibrary_id(libraryId);
                    so1.setCategory(VCF_CATEGORY);
                    so1.setContainer(ctx.getJob().getContainerId());
                    so1.setCreated(new Date());
                    so1.setModified(new Date());
                    so1.setReadset(readsetId);
                    String description = "Total samples: " + sampleCount;

                    String extraDescription = StringUtils.trimToNull(ctx.getParams().optString("jobDescription"));
                    if (extraDescription != null)
                    {
                        description = description + '\n' + extraDescription;
                    }

                    so1.setDescription(description);
                    _resumer.getFileManager().addSequenceOutput(so1);
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

    public static class Resumer extends AbstractResumer
    {
        public static final String JSON_NAME = "processVariantsCheckpoint.json";
        private static final String GENOTYPE_GVCFS = "GENOTYPE_GVCFS";

        private Map<String, File> _scatterOutputs = new HashMap<>();

        //for serialization
        public Resumer()
        {

        }

        // For use by AbstractResumer.create()
        public Resumer(JobContext ctx) throws PipelineJobException
        {
            super(ctx.getSourceDirectory(), ctx.getLogger(), ctx.getFileManager());
        }

        public static Resumer create(JobContext ctx) throws PipelineJobException
        {
            return AbstractResumer.create(ctx, JSON_NAME, Resumer.class);
        }

        @Override
        protected String getJsonName()
        {
            return JSON_NAME;
        }

        public void setStepComplete(int stepIdx, String inputFilePath, RecordedAction action, File scatterOutput) throws PipelineJobException
        {
            _scatterOutputs.put(getKey(stepIdx, inputFilePath), scatterOutput);
            _recordedActions.add(action);
            saveState();
        }

        private String getKey(int stepIdx, String inputFilePath)
        {
            return stepIdx + "<>" + inputFilePath;
        }

        public boolean isStepComplete(int stepIdx, String inputFilePath)
        {
            return _scatterOutputs.containsKey(getKey(stepIdx, inputFilePath));
        }

        public File getVcfFromStep(int stepIdx, String inputFilePath)
        {
            return _scatterOutputs.get(getKey(stepIdx, inputFilePath));
        }

        public Map<String, File> getScatterOutputs()
        {
            return _scatterOutputs;
        }

        public void setScatterOutputs(Map<String, File> scatterOutputs)
        {
            _scatterOutputs = scatterOutputs;
        }

        public void setGenotypeGVCFsComplete(RecordedAction action, File scatterOutput) throws PipelineJobException
        {
            getRecordedActions().add(action);
            _scatterOutputs.put(GENOTYPE_GVCFS, scatterOutput);
            saveState();
        }

        public boolean isGenotypeGVCFsComplete()
        {
            return _scatterOutputs.containsKey(GENOTYPE_GVCFS);
        }

        public File getGenotypeGVCFsFile()
        {
            return _scatterOutputs.get(GENOTYPE_GVCFS);
        }
    }

    public static class TestCase extends Assert
    {
        private static final Logger _log = LogManager.getLogger(ProcessVariantsHandler.TestCase.class);

        @Test
        public void serializeTest() throws Exception
        {
            ProcessVariantsHandler.Resumer r = new ProcessVariantsHandler.Resumer();
            r.setLogger(_log);
            r.setRecordedActions(new LinkedHashSet<>());
            r.setFileManager(new TaskFileManagerImpl());
            RecordedAction action1 = new RecordedAction();
            action1.setName("Action1");
            action1.setDescription("Description");
            action1.addInput(new File("/input"), "Input");
            action1.addOutput(new File("/output"), "Output", false);
            r.getRecordedActions().add(action1);

            File tmp = new File(System.getProperty("java.io.tmpdir"));
            File f = FileUtil.getAbsoluteCaseSensitiveFile(new File(tmp, ProcessVariantsHandler.Resumer.JSON_NAME));

            SequenceOutputFile so = new SequenceOutputFile();
            so.setName("so1");
            so.setFile(f);
            r.getFileManager().addSequenceOutput(so);

            r.writeToJson(tmp);

            //after deserialization the RecordedAction should match the original
            ProcessVariantsHandler.Resumer r2 = ProcessVariantsHandler.Resumer.readFromJson(f, ProcessVariantsHandler.Resumer.class);
            assertEquals(1, r2.getRecordedActions().size());
            RecordedAction action2 = r2.getRecordedActions().iterator().next();
            assertEquals("Action1", action2.getName());
            assertEquals("Description", action2.getDescription());
            assertEquals(1, action2.getInputs().size());
            assertEquals(new File("/input").toURI(), action1.getInputs().iterator().next().getURI());
            assertEquals(1, action2.getOutputs().size());
            assertEquals(new File("/output").toURI(), action2.getOutputs().iterator().next().getURI());
            assertEquals(1, r2.getFileManager().getOutputsToCreate().size());
            assertEquals("so1", r2.getFileManager().getOutputsToCreate().iterator().next().getName());
            assertEquals(f, r2.getFileManager().getOutputsToCreate().iterator().next().getFile());

            f.delete();
        }
    }

    private static Collection<String> getSamples(File input) throws PipelineJobException
    {
        if (SequenceUtil.FILETYPE.vcf.getFileType().isType(input))
        {
            try (FeatureReader<VariantContext> reader = AbstractFeatureReader.getFeatureReader(input.getPath(), new VCFCodec(), false))
            {
                VCFHeader header = (VCFHeader) reader.getHeader();
                return header.getSampleNamesInOrder();
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else if (AbstractGenomicsDBImportHandler.TILE_DB_FILETYPE.isType(input))
        {
            return AbstractGenomicsDBImportHandler.getSamplesForWorkspace(input.getParentFile());
        }
        else
        {
            throw new PipelineJobException("Unknown file type: " + input.getPath());
        }
    }

    @Override
    public void performAdditionalMergeTasks(JobContext ctx, PipelineJob job, TaskFileManager manager, ReferenceGenome genome, List<File> orderedScatterOutputs) throws PipelineJobException
    {
        List<PipelineStepCtx<VariantProcessingStep>> providers = SequencePipelineService.get().getSteps(job, VariantProcessingStep.class);
        for (PipelineStepCtx<VariantProcessingStep> stepCtx : providers)
        {
            VariantProcessingStep step = stepCtx.getProvider().create(ctx);
            if (step instanceof VariantProcessingStep.SupportsScatterGather)
            {
                ((VariantProcessingStep.SupportsScatterGather)step).performAdditionalMergeTasks(ctx, job, manager, genome, orderedScatterOutputs);
            }
        }
    }
}
