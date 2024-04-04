package org.labkey.sequenceanalysis.analysis;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.analysis.DeepVariantAnalysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by bimber on 2/3/2016.
 */
public class DeepVariantHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private final FileType _bamOrCramFileType = new FileType(Arrays.asList("bam", "cram"), "bam");

    public DeepVariantHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Run DeepVariant", "This will run DeepVariant on the selected BAMs to generate gVCF files.", null, DeepVariantAnalysis.getToolDescriptors());
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _bamOrCramFileType.isType(o.getFile());
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
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean requiresSingleGenome()
    {
        return false;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            String modelType = ctx.getParams().optString("modelType");
            DeepVariantAnalysis.inferModelType(modelType, ctx);
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            if (inputFiles.size() != 1)
            {
                throw new PipelineJobException("Expected a single input file");
            }

            SequenceOutputFile so = inputFiles.get(0);

            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            action.addInput(so.getFile(), "Input BAM File");

            File outputFile = new File(ctx.getOutputDir(), FileUtil.getBaseName(so.getFile()) + ".g.vcf.gz");
            File outputFileVcf = new File(ctx.getOutputDir(), FileUtil.getBaseName(so.getFile()) + ".vcf.gz");

            DeepVariantAnalysis.DeepVariantWrapper wrapper = new DeepVariantAnalysis.DeepVariantWrapper(job.getLogger());
            wrapper.setOutputDir(ctx.getOutputDir());

            ReferenceGenome referenceGenome = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());
            if (referenceGenome == null)
            {
                throw new PipelineJobException("No reference genome found for output: " + so.getRowid());
            }

            String inferredModelType = ctx.getSequenceSupport().getCachedObject("modelType", String.class);
            String modelType = inferredModelType == null ? ctx.getParams().optString("modelType") : inferredModelType;
            if (modelType == null)
            {
                throw new PipelineJobException("Missing model type");
            }

            List<String> args = new ArrayList<>(getClientCommandArgs(ctx.getParams()));
            args.add("--model_type=" + modelType);

            String binVersion = ctx.getParams().optString("binVersion");
            if (binVersion == null)
            {
                throw new PipelineJobException("Missing binVersion");
            }

            boolean retainVcf = ctx.getParams().optBoolean("retainVcf", false);
            wrapper.execute(so.getFile(), referenceGenome.getWorkingFastaFile(), outputFile, retainVcf, ctx.getFileManager(), binVersion, args);

            action.addOutput(outputFile, "gVCF File", false);

            SequenceOutputFile o = new SequenceOutputFile();
            o.setName(outputFile.getName());
            o.setFile(outputFile);
            o.setLibrary_id(so.getLibrary_id());
            o.setCategory("DeepVariant gVCF File");
            o.setReadset(so.getReadset());
            o.setDescription("DeepVariant Version: " + binVersion);

            ctx.addSequenceOutput(o);

            if (retainVcf)
            {
                SequenceOutputFile vcf = new SequenceOutputFile();
                vcf.setName(outputFileVcf.getName());
                vcf.setFile(outputFileVcf);
                vcf.setLibrary_id(so.getLibrary_id());
                vcf.setCategory("DeepVariant VCF File");
                vcf.setReadset(so.getReadset());
                vcf.setDescription("DeepVariant Version: " + binVersion);

                ctx.addSequenceOutput(vcf);
            }

            ctx.addActions(action);
        }

        private List<String> getClientCommandArgs(JSONObject params)
        {
            List<String> ret = new ArrayList<>();

            for (ToolParameterDescriptor desc : getParameters())
            {
                if (desc.getCommandLineParam() != null)
                {
                    String val = params.optString(desc.getName(), null);
                    if (StringUtils.trimToNull(val) != null)
                    {
                        ret.addAll(desc.getCommandLineParam().getArguments(" ", val));
                    }
                }
            }

            return ret;
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }
}