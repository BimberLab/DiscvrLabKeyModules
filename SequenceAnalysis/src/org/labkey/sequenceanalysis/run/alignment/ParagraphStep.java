package org.labkey.sequenceanalysis.run.alignment;

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
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParagraphStep extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public ParagraphStep()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Paragraph SV Genotyping", "This will run paraGRAPH on one or more BAM files to genotype SVs", null, Arrays.asList(
                ToolParameterDescriptor.createExpDataParam("svVCF", "Input VCF", "This is the DataId of the VCF containing the SVs to genotype", "ldk-expdatafield", new JSONObject()
                {{
                    put("allowBlank", false);
                }}, null)
        ));
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
            File inputVCF = ctx.getSequenceSupport().getCachedData(ctx.getParams().getInt("svVCF"));
            if (!inputVCF.exists())
            {
                throw new PipelineJobException("Unable to find file: " + inputVCF.getPath());
            }

            Integer threads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            for (SequenceOutputFile so : inputFiles)
            {
                List<String> depthArgs = new ArrayList<>();
                depthArgs.add("idxdepth");
                depthArgs.add("-b");
                depthArgs.add(so.getFile().getPath());

                File coverageFile = new File(ctx.getWorkingDirectory(), "coverage.txt");
                depthArgs.add("-o");
                depthArgs.add(coverageFile.getPath());

                depthArgs.add("-r");
                depthArgs.add(ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id()).getWorkingFastaFile().getPath());

                if (threads != null)
                {
                    depthArgs.add("--threads");
                    depthArgs.add(threads.toString());
                }

                new SimpleScriptWrapper(ctx.getLogger()).execute(depthArgs);

                if (!coverageFile.exists())
                {
                    throw new PipelineJobException("Missing file: " + coverageFile.getPath());
                }

                // Should produce a simple text file:
                //    id  path    depth   read length
                //    TNPRC-IB18  ../IB18.cram 29.77   150

                List<String> paragraphArgs = new ArrayList<>();
                paragraphArgs.add("multigrmpy.py");
                paragraphArgs.add("--verbose");

                File paragraphOut = new File(ctx.getWorkingDirectory(), FileUtil.getBaseName(so.getFile()) + ".paragraph.txt");
                paragraphArgs.add("-o");
                paragraphArgs.add(paragraphOut.getPath());

                int svVcfId = ctx.getParams().optInt("svVCF");
                if (svVcfId == 0)
                {
                    throw new PipelineJobException("Missing svVCF ID");
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

                paragraphArgs.add("-i");
                paragraphArgs.add(svVcf.getPath());

                paragraphArgs.add("-m");
                paragraphArgs.add(coverageFile.getPath());

                paragraphArgs.add("-r");
                paragraphArgs.add(ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id()).getWorkingFastaFile().getPath());

                paragraphArgs.add("--scratch-dir");
                paragraphArgs.add(SequencePipelineService.get().getJavaTempDir());

                if (threads != null)
                {
                    paragraphArgs.add("--threads");
                    paragraphArgs.add(threads.toString());
                }

                paragraphArgs.add("--logfile");
                paragraphArgs.add(new File(ctx.getWorkingDirectory(), "paragraph.log").getPath());

                new SimpleScriptWrapper(ctx.getLogger()).execute(paragraphArgs);

                File genotypes = new File(ctx.getWorkingDirectory(), "genotypes.vcf.gz");
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

                ctx.getFileManager().addSequenceOutput(genotypes, "paraGRAPH Genotypes: " + so.getName(), "paraGRAPH Genoypes", so.getReadset(), null, so.getLibrary_id(), "Input VCF: " + svVcf.getName() + " (" + svVcfId + ")");
            }
        }
    }
}