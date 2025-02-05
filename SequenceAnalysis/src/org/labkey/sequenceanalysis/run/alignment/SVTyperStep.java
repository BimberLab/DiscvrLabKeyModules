package org.labkey.sequenceanalysis.run.alignment;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//https://github.com/hall-lab/svtyper

public class SVTyperStep extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public SVTyperStep()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "SVTyper SV Genotyping", "This will run SVTyper on one or more BAM files to genotype SVs", null, Arrays.asList(
                ToolParameterDescriptor.createExpDataParam("svVCF", "Input VCF", "This is the DataId of the VCF containing the SVs to genotype", "ldk-expdatafield", new JSONObject()
                {{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
        ));
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && SequenceUtil.FILETYPE.bamOrCram.getFileType().isType(o.getFile());
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

    public static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            int svVcfId = ctx.getParams().optInt("svVCF", 0);
            if (svVcfId == 0)
            {
                throw new PipelineJobException("svVCF param was null");
            }

            File svVcf = ctx.getSequenceSupport().getCachedData(svVcfId);
            if (svVcf == null)
            {
                throw new PipelineJobException("File not found for ID: " + svVcfId);
            }
            else if (!svVcf.exists())
            {
                throw new PipelineJobException("Missing file: " + svVcf.getPath());
            }

            Integer threads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());

            for (SequenceOutputFile so : inputFiles)
            {
                List<String> jsonArgs = new ArrayList<>();
                SimpleScriptWrapper wrapper = new SimpleScriptWrapper(ctx.getLogger());
                jsonArgs.add(AbstractCommandWrapper.resolveFileInPath("svtyper", null, true).getPath());
                jsonArgs.add("-B");
                jsonArgs.add(so.getFile().getPath());

                File coverageJson = new File(ctx.getWorkingDirectory(), "bam.json");
                jsonArgs.add("-l");
                jsonArgs.add(coverageJson.getPath());

                jsonArgs.add("--verbose");

                File doneFile = new File(ctx.getWorkingDirectory(), "json.done");
                ctx.getFileManager().addIntermediateFile(doneFile);
                if (doneFile.exists())
                {
                    ctx.getLogger().info("BAM json already generated, skipping");
                }
                else
                {
                    wrapper.execute(jsonArgs);
                    try
                    {
                        FileUtils.touch(doneFile);
                        ctx.getFileManager().addIntermediateFile(doneFile);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }

                if (!coverageJson.exists())
                {
                    throw new PipelineJobException("Missing file: " + coverageJson.getPath());
                }
                ctx.getFileManager().addIntermediateFile(coverageJson);

                List<String> svtyperArgs = new ArrayList<>();
                svtyperArgs.add(AbstractCommandWrapper.resolveFileInPath("svtyper-sso", null, true).getPath());

                svtyperArgs.add("-i");
                svtyperArgs.add(svVcf.getPath());

                svtyperArgs.add("-B");
                svtyperArgs.add(so.getFile().getPath());

                svtyperArgs.add("-l");
                svtyperArgs.add(coverageJson.getPath());

                svtyperArgs.add("--verbose");

                if (threads != null)
                {
                    svtyperArgs.add("--core");
                    svtyperArgs.add(threads.toString());
                }

                File genotypes = new File(ctx.getWorkingDirectory(), SequenceAnalysisService.get().getUnzippedBaseName(so.getName()) + ".svtyper.vcf.gz");
                wrapper.execute(Arrays.asList("/bin/bash", "-c", StringUtils.join(svtyperArgs, " ") + "| bgzip -c"), ProcessBuilder.Redirect.to(genotypes));

                if (!genotypes.exists())
                {
                    throw new PipelineJobException("Missing file: " + genotypes.getPath());
                }

                try
                {
                    SequenceAnalysisService.get().ensureVcfIndex(genotypes, ctx.getLogger());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                ctx.getFileManager().addSequenceOutput(genotypes, "SVTyper Genotypes: " + so.getName(), "SVTyper Genoypes", so.getReadset(), null, so.getLibrary_id(), "Input VCF: " + svVcf.getName() + " (" + svVcfId + ")");
            }
        }
    }
}
