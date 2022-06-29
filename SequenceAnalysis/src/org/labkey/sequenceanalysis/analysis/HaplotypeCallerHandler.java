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
import org.labkey.sequenceanalysis.run.analysis.HaplotypeCallerAnalysis;
import org.labkey.sequenceanalysis.run.util.HaplotypeCallerWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by bimber on 2/3/2016.
 */
public class HaplotypeCallerHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _bamOrCramFileType = new FileType(Arrays.asList("bam", "cram"), "bam");

    public HaplotypeCallerHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Run GATK HaplotypeCaller", "This will run GATK HaplotypeCaller on the selected BAMs to generate gVCF files.", null, HaplotypeCallerAnalysis.getToolDescriptors());
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

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            JSONObject params = ctx.getParams();

            if (inputFiles.isEmpty())
            {
                job.getLogger().warn("no input files");
            }

            for (SequenceOutputFile so : inputFiles)
            {
                RecordedAction action = new RecordedAction(getName());
                action.setStartTime(new Date());

                action.addInput(so.getFile(), "Input BAM File");

                File outputFile = new File(ctx.getOutputDir(), FileUtil.getBaseName(so.getFile()) + ".g.vcf.gz");
                File idxFile = new File(ctx.getOutputDir(), FileUtil.getBaseName(so.getFile()) + ".g.vcf.gz.idx");

                HaplotypeCallerWrapper wrapper = new HaplotypeCallerWrapper(job.getLogger());
                wrapper.setOutputDir(ctx.getOutputDir());

                ReferenceGenome referenceGenome = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());
                if (referenceGenome == null)
                {
                    throw new PipelineJobException("No reference genome found for output: " + so.getRowid());
                }

                List<String> args = new ArrayList<>();
                args.addAll(getClientCommandArgs(params));

                wrapper.execute(so.getFile(), referenceGenome.getWorkingFastaFile(), outputFile, args);

                action.addOutput(outputFile, "gVCF File", false);
                if (idxFile.exists())
                {
                    action.addOutput(idxFile, "VCF Index", false);
                }

                SequenceOutputFile o = new SequenceOutputFile();
                o.setName(outputFile.getName());
                o.setFile(outputFile);
                o.setLibrary_id(so.getLibrary_id());
                o.setCategory("gVCF File");
                o.setReadset(so.getReadset());
                o.setDescription("GATK Version: " + wrapper.getVersionString());
                ctx.addSequenceOutput(o);

                ctx.addActions(action);
            }
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