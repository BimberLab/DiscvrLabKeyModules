package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.json.old.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReblockGvcfHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public ReblockGvcfHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "ReblockGVCF", "This will run GATK ReblockGVCF, potentially replacing the original", null, Arrays.asList(
                ToolParameterDescriptor.create("replaceOriginal", "Replace Original File", "If selected, the input gVCF will be deleted and the database record will be switched to use this filepath.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
            )
        );
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && SequenceUtil.FILETYPE.gvcf.getFileType().isType(o.getFile());
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
            ctx.getLogger().info("Replace input gVCF: " + replaceOriginal);

            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getLogger().info("Input file size: " + FileUtils.byteCountToDisplaySize(so.getFile().length()));

                ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());
                File reblocked = getReblockedName(so.getFile());
                new ReblockGvcfWrapper(ctx.getLogger()).execute(so.getFile(), reblocked, genome.getWorkingFastaFile());

                if (replaceOriginal)
                {
                    ctx.getLogger().info("Deleting original gVCF: " + so.getFile().getPath());
                    new File(so.getFile().getPath() + ".tbi").delete();
                    so.getFile().delete();
                }

                ctx.getLogger().info("Reblocked file size: " + FileUtils.byteCountToDisplaySize(reblocked.length()));
            }
        }

        private File getReblockedName(File gvcf)
        {
            return new File(gvcf.getParentFile(), gvcf.getName().replaceAll("g.vcf", "rb.g.vcf"));
        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            List<Map<String, Object>> toUpdate = new ArrayList<>();
            List<Map<String, Object>> oldKeys = inputs.stream().map(x -> {
                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                row.put("rowid", x.getRowid());
                return(row);
            }).collect(Collectors.toList());

            for (SequenceOutputFile so : inputs)
            {
                File reblocked = getReblockedName(so.getFile());
                if (!reblocked.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + reblocked.getPath());
                }

                job.getLogger().info("Updating ExpData record with new filepath: " + reblocked.getPath());
                ExpData d = so.getExpData();
                d.setDataFileURI(reblocked.toURI());
                d.setName(reblocked.getName());
                d.save(job.getUser());

                if (so.getName().contains(".g.vcf.gz"))
                {
                    Map<String, Object> row = new CaseInsensitiveHashMap<>();
                    row.put("rowid", so.getRowid());
                    row.put("container", so.getContainer());
                    row.put("name", so.getName().replaceAll("\\.g.vcf", "\\.rb.g.vcf"));
                    row.put("description", (so.getDescription() == null ? "" : so.getDescription() + "\n") + "ReblockGVCF performed");
                    toUpdate.add(row);
                }
            }

            try
            {
                Container target = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
                QueryService.get().getUserSchema(job.getUser(), target, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES).getUpdateService().updateRows(job.getUser(), target, toUpdate, oldKeys, null, null);
            }
            catch (QueryUpdateServiceException | InvalidKeyException | BatchValidationException | SQLException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    public static class ReblockGvcfWrapper extends AbstractGatk4Wrapper
    {
        public ReblockGvcfWrapper(Logger log)
        {
            super(log);
        }

        public void execute(File gVCF, File reblockOutput, File referenceFasta) throws PipelineJobException
        {
            List<String> reblockArgs = new ArrayList<>(getBaseArgs());
            reblockArgs.add("ReblockGVCF");
            reblockArgs.add("-R");
            reblockArgs.add(referenceFasta.getPath());
            reblockArgs.add("-V");
            reblockArgs.add(gVCF.getPath());
            reblockArgs.add("-O");
            reblockArgs.add(reblockOutput.getPath());

            execute(reblockArgs);
            if (!reblockOutput.exists())
            {
                throw new PipelineJobException("Expected output not found: " + reblockOutput.getPath());
            }
        }
    }
}
