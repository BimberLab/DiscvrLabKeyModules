package org.labkey.sequenceanalysis.pipeline;

import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureReader;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.log4j.Logger;
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
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
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
public class ProcessVariantsHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>, SequenceOutputHandler.HasActionNames
{
    private FileType _vcfFileType = new FileType(Arrays.asList(".vcf"), ".vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);
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
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public static void initVariantProcessing(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, File outputDir) throws PipelineJobException
    {
        List<PipelineStepCtx<VariantProcessingStep>> providers = SequencePipelineService.get().getSteps(job, VariantProcessingStep.class);
        boolean requiresPedigree = false;
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

    public static File processVCF(File input, Integer libraryId, JobContext ctx, Resumer resumer) throws PipelineJobException
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

        for (PipelineStepCtx<VariantProcessingStep> stepCtx : providers)
        {
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

            ctx.getFileManager().addIntermediateFile(currentVCF);
            ctx.getFileManager().addIntermediateFile(vcfIdx);

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(libraryId);
            action.addInput(genome.getSourceFastaFile(), "Reference FASTA");

            VariantProcessingStep step = stepCtx.getProvider().create(ctx);
            step.setStepIdx(stepCtx.getStepIdx());

            VariantProcessingStep.Output output = step.processVariants(currentVCF, ctx.getOutputDir(), genome);
            ctx.getFileManager().addStepOutputs(action, output);

            if (output.getVCF() != null)
            {
                currentVCF = output.getVCF();

                ctx.getJob().getLogger().info("total variants: " + getVCFLineCount(currentVCF, ctx.getJob().getLogger(), false));
                ctx.getJob().getLogger().info("passing variants: " + getVCFLineCount(currentVCF, ctx.getJob().getLogger(), true));
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
            ctx.getFileManager().removeIntermediateFile(currentVCF);
            ctx.getFileManager().removeIntermediateFile(new File(currentVCF.getPath() + ".tbi"));

            return currentVCF;
        }

        ctx.getLogger().debug("no VCF produced at end of processing");

        return null;
    }

    public static String getVCFLineCount(File vcf, Logger log, boolean passOnly) throws PipelineJobException
    {
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
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            initVariantProcessing(job, support, inputFiles, outputDir);
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            _resumer = Resumer.create((JobContextImpl)ctx);
            if (_resumer.isResume())
            {
                ctx.getLogger().info("resuming previous job");
                if (_resumer.getFileManager() == null)
                {
                    throw new PipelineJobException("fileManager is null for resumed job");
                }

                ((JobContextImpl)ctx).setFileManager(_resumer.getFileManager());
            }

            for (SequenceOutputFile input : inputFiles)
            {
                File processed = processVCF(input.getFile(), input.getLibrary_id(), ctx, _resumer);
                if (processed != null && processed.exists())
                {
                    ctx.getLogger().debug("adding sequence output: " + processed.getPath());
                    if (input.getFile().equals(processed))
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
                        so1.setLibrary_id(input.getLibrary_id());
                        so1.setCategory("VCF File");
                        so1.setContainer(ctx.getJob().getContainerId());
                        so1.setCreated(new Date());
                        so1.setModified(new Date());
                        so1.setReadset(inputFiles.iterator().next().getReadset());
                        so1.setDescription("Total samples: " + sampleCount);

                        _resumer.addSequenceOutput(so1);
                    }
                }
            }

            _resumer.markComplete(ctx);
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

        private Map<String, File> _finalVcfs = new HashMap<>();
        private Set<SequenceOutputFile> _sequenceOutputFiles = new HashSet<>();

        //for serialization
        public Resumer()
        {

        }

        private Resumer(JobContextImpl ctx)
        {
            super(ctx.getSourceDirectory(), ctx.getLogger(), (TaskFileManagerImpl)ctx.getFileManager());
        }

        public static Resumer create(JobContextImpl ctx) throws PipelineJobException
        {
            File json = getSerializedJson(ctx.getSourceDirectory(), JSON_NAME);
            if (!json.exists())
            {
                return new Resumer(ctx);
            }
            else
            {
                Resumer ret = readFromJson(json, Resumer.class);
                ret._isResume = true;
                ret._log = ctx.getLogger();
                ret._localWorkDir = ctx.getWorkDir().getDir();
                ret._fileManager._job = (SequenceOutputHandlerJob)ctx.getJob();
                ret._fileManager._wd = ctx.getWorkDir();
                ret._fileManager._workLocation = ctx.getWorkDir().getDir();
                ctx.setFileManager(ret._fileManager);
                try
                {
                    if (!ret._copiedInputs.isEmpty())
                    {
                        for (File orig : ret._copiedInputs.keySet())
                        {
                            ctx.getWorkDir().inputFile(orig, ret._copiedInputs.get(orig), false);
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                //debugging:
                ctx.getLogger().debug("loaded from file.  total recorded actions: " + ret.getRecordedActions().size());
                for (RecordedAction a : ret.getRecordedActions())
                {
                    ctx.getLogger().debug("action: " + a.getName() + ", inputs: " + a.getInputs().size() + ", outputs: " + a.getOutputs().size());
                }

                if (ret._recordedActions == null)
                {
                    throw new PipelineJobException("Job read from file, but did not have any saved actions.  This indicates a problem w/ serialization.");
                }

                return ret;
            }
        }

        public void markComplete(JobContext ctx)
        {
            ctx.getLogger().debug("total sequence outputs tracked in resumer: " + getSequenceOutputFiles().size());
            for (SequenceOutputFile so : getSequenceOutputFiles())
            {
                ctx.addSequenceOutput(so);
            }

            ctx.getLogger().debug("total actions tracked in resumer: " + getRecordedActions().size());
            for (RecordedAction a : getRecordedActions())
            {
                ctx.addActions(a);
            }

            super.markComplete();
        }

        @Override
        protected String getJsonName()
        {
            return JSON_NAME;
        }

        public void setStepComplete(int stepIdx, String inputFilePath, RecordedAction action, File finalVCF) throws PipelineJobException
        {
            _finalVcfs.put(getKey(stepIdx, inputFilePath), finalVCF);
            _recordedActions.add(action);
            saveState();
        }

        @Override
        protected void logInfoBeforeSave()
        {
            super.logInfoBeforeSave();

            _log.debug("total sequence outputs: " + _sequenceOutputFiles.size());
        }

        private String getKey(int stepIdx, String inputFilePath)
        {
            return stepIdx + "<>" + inputFilePath;
        }

        public boolean isStepComplete(int stepIdx, String inputFilePath)
        {
            return _finalVcfs.containsKey(getKey(stepIdx, inputFilePath));
        }

        public File getVcfFromStep(int stepIdx, String inputFilePath)
        {
            return _finalVcfs.get(getKey(stepIdx, inputFilePath));
        }

        public Set<SequenceOutputFile> getSequenceOutputFiles()
        {
            return _sequenceOutputFiles;
        }

        public void setSequenceOutputFiles(Set<SequenceOutputFile> sequenceOutputFiles)
        {
            _sequenceOutputFiles = sequenceOutputFiles;
        }

        public void addSequenceOutput(SequenceOutputFile sequenceOutputFile)
        {
            _sequenceOutputFiles.add(sequenceOutputFile);
        }

        public Map<String, File> getFinalVcfs()
        {
            return _finalVcfs;
        }

        public void setFinalVcfs(Map<String, File> finalVcfs)
        {
            _finalVcfs = finalVcfs;
        }

        public void setGenotypeGVCFsComplete(RecordedAction action, File finalVCF)
        {
            getRecordedActions().add(action);
            _finalVcfs.put(GENOTYPE_GVCFS, finalVCF);
        }

        public boolean isGenotypeGVCFsComplete()
        {
            return _finalVcfs.containsKey(GENOTYPE_GVCFS);
        }

        public File getGenotypeGVCFsFile()
        {
            return _finalVcfs.get(GENOTYPE_GVCFS);
        }
    }

    public static class TestCase extends Assert
    {
        private static final Logger _log = Logger.getLogger(ProcessVariantsHandler.TestCase.class);

        @Test
        public void serializeTest() throws Exception
        {
            ProcessVariantsHandler.Resumer r = new ProcessVariantsHandler.Resumer();
            r._log = _log;
            r._recordedActions = new LinkedHashSet<>();
            RecordedAction action1 = new RecordedAction();
            action1.setName("Action1");
            action1.setDescription("Description");
            action1.addInput(new File("/input"), "Input");
            action1.addOutput(new File("/output"), "Output", false);
            r._recordedActions.add(action1);

            File tmp = new File(System.getProperty("java.io.tmpdir"));
            File f = FileUtil.getAbsoluteCaseSensitiveFile(new File(tmp, ProcessVariantsHandler.Resumer.JSON_NAME));

            r._sequenceOutputFiles = new HashSet<>();
            SequenceOutputFile so = new SequenceOutputFile();
            so.setName("so1");
            so.setFile(f);
            r._sequenceOutputFiles.add(so);

            r.writeToJson(tmp);

            //after deserialization the RecordedAction should match the original
            ProcessVariantsHandler.Resumer r2 = ProcessVariantsHandler.Resumer.readFromJson(f, ProcessVariantsHandler.Resumer.class);
            assertEquals(1, r2._recordedActions.size());
            RecordedAction action2 = r2._recordedActions.iterator().next();
            assertEquals("Action1", action2.getName());
            assertEquals("Description", action2.getDescription());
            assertEquals(1, action2.getInputs().size());
            assertEquals(new File("/input").toURI(), action1.getInputs().iterator().next().getURI());
            assertEquals(1, action2.getOutputs().size());
            assertEquals(new File("/output").toURI(), action2.getOutputs().iterator().next().getURI());
            assertEquals(1, r2._sequenceOutputFiles.size());
            assertEquals("so1", r2._sequenceOutputFiles.iterator().next().getName());
            assertEquals(f, r2._sequenceOutputFiles.iterator().next().getFile());

            f.delete();
        }
    }
}
