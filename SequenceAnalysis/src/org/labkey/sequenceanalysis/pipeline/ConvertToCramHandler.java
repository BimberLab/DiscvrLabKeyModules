package org.labkey.sequenceanalysis.pipeline;

import org.json.JSONObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsCramConverter;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ConvertToCramHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public ConvertToCramHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Convert To Cram", "This will convert a BAM file to CRAM, replacing the original", null, Arrays.asList(
                ToolParameterDescriptor.create("replaceOriginal", "Replace Original File", "If selected, the input BAM will be deleted and the database record will be switched to use this filepath.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true))
        );
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && SequenceUtil.FILETYPE.bam.getFileType().isType(o.getFile());
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
    public boolean requiresGenome()
    {
        return true;
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

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            boolean replaceOriginal = ctx.getParams().optBoolean("replaceOriginal", false);
            ctx.getLogger().info("Replace input BAM: " + replaceOriginal);

            Integer threads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            for (SequenceOutputFile so : inputFiles)
            {
                ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());
                File cram = new File(so.getFile().getParentFile(), FileUtil.getBaseName(so.getFile()) + ".cram");
                new SamtoolsCramConverter(ctx.getLogger()).convert(so.getFile(), cram, genome.getWorkingFastaFileGzipped(), true, threads);
                checkCramAndIndex(so);

                if (replaceOriginal)
                {
                    ctx.getLogger().info("Deleting original BAM: " + so.getFile().getPath());
                    new File(so.getFile().getPath() + ".bai").delete();
                    so.getFile().delete();
                }
            }
        }

        private void checkCramAndIndex(SequenceOutputFile so) throws PipelineJobException
        {
            File cram = new File(so.getFile().getParentFile(), FileUtil.getBaseName(so.getFile()) + ".cram");
            if (!cram.exists())
            {
                throw new PipelineJobException("Unable to find file: " + cram.getPath());
            }

            File cramIdx = new File(cram.getPath() + ".crai");
            if (!cramIdx.exists())
            {
                throw new PipelineJobException("Unable to find file: " + cramIdx.getPath());
            }
        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {

            for (SequenceOutputFile so : inputs)
            {
                File cram = new File(so.getFile().getParentFile(), FileUtil.getBaseName(so.getFile()) + ".cram");
                checkCramAndIndex(so);

                job.getLogger().info("Updating ExpData record with new filepath: " + cram.getPath());
                ExpData d = so.getExpData();
                d.setDataFileURI(cram.toURI());
                d.save(job.getUser());
            }
        }
    }
}
