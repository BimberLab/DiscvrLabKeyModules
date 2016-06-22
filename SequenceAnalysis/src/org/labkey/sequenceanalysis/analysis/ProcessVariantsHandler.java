package org.labkey.sequenceanalysis.analysis;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 8/26/2014.
 */
public class ProcessVariantsHandler implements SequenceOutputHandler
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
        //TODO
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

    public class Processor implements OutputProcessor
    {
        private SequenceTaskHelper _taskHelper;

        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            File outDir = ((FileAnalysisJobSupport) job).getAnalysisDirectory();
            _taskHelper = new SequenceTaskHelper(job, outDir);

            for (SequenceOutputFile input : inputFiles)
            {
                File currentVCF = input.getFile();

                job.getLogger().info("***Starting processing of file: " + input.getName());
                job.setStatus(PipelineJob.TaskStatus.running, "Processing: " + input.getName());

                List<PipelineStepProvider<VariantProcessingStep>> providers = SequencePipelineService.get().getSteps(job, VariantProcessingStep.class);
                for (PipelineStepProvider<VariantProcessingStep> provider : providers)
                {
                    RecordedAction action = new RecordedAction(provider.getLabel());
                    Date start = new Date();
                    action.setStartTime(start);
                    getHelper().getFileManager().addInput(action, "Input VCF", currentVCF);

                    VariantProcessingStep step = provider.create(getHelper());
                    VariantProcessingStep.Output output = step.processVariants(currentVCF, support.getCachedGenome(input.getLibrary_id()));
                    getHelper().getFileManager().addStepOutputs(action, output);

                    if (output.getVCF() != null)
                    {
                        currentVCF = output.getVCF();
                    }
                    else
                    {
                        throw new PipelineJobException("no output VCF produced");
                    }

                    job.getLogger().info("total variants: " + getVCFLineCount(currentVCF, job.getLogger()));
                    job.getLogger().debug("index exists: " + (new File(currentVCF.getPath() + ".tbi")).exists());

                    if (!output.getCommandsExecuted().isEmpty())
                    {
                        int commandIdx = 0;
                        for (String command : output.getCommandsExecuted())
                        {
                            action.addParameter(new RecordedAction.ParameterType("command" + commandIdx, PropertyType.STRING), command);
                            commandIdx++;
                        }
                    }

                    Date end = new Date();
                    action.setEndTime(end);
                    job.getLogger().info(provider.getLabel() + " Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
                    actions.add(action);
                }

                if (currentVCF.exists())
                {
                    SequenceOutputFile so1 = new SequenceOutputFile();
                    so1.setName(currentVCF.getName());
                    //TODO
                    //so1.setDescription("GATK GenotypeGVCF output");
                    so1.setFile(currentVCF);
                    so1.setLibrary_id(input.getLibrary_id());
                    so1.setCategory("VCF File");
                    so1.setContainer(job.getContainerId());
                    so1.setCreated(new Date());
                    so1.setModified(new Date());

                    outputsToCreate.add(so1);
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        private String getVCFLineCount(File vcf, Logger log) throws PipelineJobException
        {
            String cat = vcf.getName().endsWith(".gz") ? "zcat" : "cat";
            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(log);

            return wrapper.executeWithOutput(Arrays.asList("/bin/sh", "-c '" + cat + " \"" + vcf.getPath() + "\" | grep -v \"#\" | wc -l | awk \" { print $1 } \"'"));
        }

        private SequenceTaskHelper getHelper()
        {
            return _taskHelper;
        }
    }
}
