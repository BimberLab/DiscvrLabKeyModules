package org.labkey.sequenceanalysis.run.alignment;

import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
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

public class Graphtyper extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public Graphtyper()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Graphtyper (SV Genotyping)", "This will run Graphtyper on one or more BAM files to genotype SVs", null, Arrays.asList(
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
                List<String> args = new ArrayList<>();
                SimpleScriptWrapper wrapper = new SimpleScriptWrapper(ctx.getLogger());
                args.add(AbstractCommandWrapper.resolveFileInPath("graphtyper", null, true).getPath());
                args.add("genotype_sv");

                ReferenceGenome rg = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());
                if (rg == null)
                {
                    throw new PipelineJobException("Missing reference genome: " + so.getLibrary_id());
                }

                args.add(rg.getWorkingFastaFile().getPath());
                args.add(svVcf.toString());

                args.add("--sam");
                args.add(so.getFile().getPath());

                if (threads != null)
                {
                    args.add("--threads");
                    args.add(threads.toString());
                }

                wrapper.execute(args);
                
                File genotypes = new File(ctx.getWorkingDirectory(), "sv_results/" + SequenceAnalysisService.get().getUnzippedBaseName(so.getName()) + ".vcf.gz");
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

                ctx.getFileManager().addSequenceOutput(genotypes, "Graphtyper Genotypes: " + so.getName(), "Graphtyper Genoypes", so.getReadset(), null, so.getLibrary_id(), "Input VCF: " + svVcf.getName() + " (" + svVcfId + ")");
            }
        }
    }
}
