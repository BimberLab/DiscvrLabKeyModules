package org.labkey.sequenceanalysis.analysis;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.pipeline.SequenceJobSupportImpl;
import org.labkey.sequenceanalysis.run.alignment.CellRangerWrapper;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CellRangerReanalysisHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("cloupe", false);

    public CellRangerReanalysisHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "CellRanger Reanalyze", "This will re-run analysis on 10x GEX data, which could allow re-extraction of data specifying a custom cell number.", null, Arrays.asList(
                ToolParameterDescriptor.create("force-cells", "Force Cells", "Force pipeline to use this number of cells, bypassing the cell detection algorithm. Use this if the number of cells estimated by Cell Ranger is not consistent with the barcode rank plot.", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 0);
                }}, null),
                ToolParameterDescriptor.create("deletePrevious", "Replace Previous Loupe File", "If checked, the output file record for the select cloupe files will be deleted if reanalysis runs completely.  Only the database record will be deleted, not the actual file itself.  This can help reduce clutter and avoid confusion.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && (
                _fileType.isType(o.getFile()) || (o.getFile().getName().endsWith("web_summary.html") && "10x Run Summary".equals(o.getCategory()))
        );
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
        return new CellRangerReanalysisHandler.Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile so : inputFiles)
            {
                if (so.getReadset() != null)
                {
                    ((SequenceJobSupportImpl) support).cacheReadset(so.getReadset(), job.getUser());
                }
                else
                {
                    job.getLogger().error("Output file lacks a readset and will be skipped: " + so.getRowid());
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            if (job.getParameters().containsKey("deletePrevious") && "true".equals(job.getParameters().get("deletePrevious").toLowerCase()))
            {
                int loupeCreated = 0;
                for (SequenceOutputFile so : outputsCreated)
                {
                    if ("10x Loupe File".equals(so.getCategory()))
                    {
                        loupeCreated++;
                    }
                }

                if (loupeCreated == inputs.size())
                {
                    job.getLogger().warn("deleting previous loupe output file records");
                    List<Map<String, Object>> toDelete = new ArrayList<>();
                    for (SequenceOutputFile o : inputs)
                    {
                        if (_fileType.isType(o.getFile()))
                        {
                            job.getLogger().debug("deleting output record: " + o.getRowid());
                            Map<String, Object> row = new CaseInsensitiveHashMap<>();
                            row.put("rowid", o.getRowid());
                            row.put("container", o.getContainer());
                            toDelete.add(row);
                        }
                        else
                        {
                            job.getLogger().debug("will not delete file: " + o.getFile().getName());
                        }
                    }

                    if (!toDelete.isEmpty())
                    {
                        try
                        {
                            Container target = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
                            TableInfo ti = QueryService.get().getUserSchema(job.getUser(), target, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
                            Map<String, Object> scriptContext = new HashMap<>();
                            scriptContext.put("deleteFromServer", true);  //a flag to make the trigger script accept this
                            ti.getUpdateService().deleteRows(job.getUser(), target, toDelete, null, scriptContext);
                        }
                        catch (InvalidKeyException | BatchValidationException | SQLException | QueryUpdateServiceException e)
                        {
                            throw new PipelineJobException(e);
                        }
                    }
                }
                else
                {
                    job.getLogger().warn("loupe files created does not equal number of inputs, will not delete previous records");
                }
            }
            else
            {
                job.getLogger().debug("previous records will not be delted");
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile so : inputFiles)
            {
                CellRangerWrapper wrapper = new CellRangerWrapper(ctx.getLogger());

                File matrix = new File(so.getFile().getParentFile(), "raw_gene_bc_matrices_h5.h5");
                if (!matrix.exists())
                {
                    throw new PipelineJobException("Unable to find gene count matrix: " + matrix.getPath());
                }

                List<String> params = new ArrayList<>();
                if (ctx.getParams().get("force-cells") != null)
                {
                    params.add("--force-cells=" + ctx.getParams().get("force-cells"));
                }

                Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                ReferenceGenome referenceGenome = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());

                File output = wrapper.runReanalyze(matrix, ctx.getOutputDir(), rs.getName(), params);

                try
                {
                    File out = new File(output, "outs");

                    String prefix = FileUtil.makeLegalName(rs.getName() + "_");
                    File outputHtml = new File(out, "web_summary.html");
                    if (!outputHtml.exists())
                    {
                        throw new PipelineJobException("Unable to find file: " + outputHtml.getPath());
                    }

                    File outputHtmlRename = new File(out, prefix + outputHtml.getName());
                    FileUtils.moveFile(outputHtml, outputHtmlRename);
                    ctx.getFileManager().addSequenceOutput(outputHtmlRename, rs.getName() + " 10x Reanalyze Summary", "10x Run Summary", rs.getRowId(), null, referenceGenome.getGenomeId(), null);

                    File loupe = new File(out, "cloupe.cloupe");
                    if (loupe.exists())
                    {
                        File loupeRename = new File(out, prefix + loupe.getName());
                        FileUtils.moveFile(loupe, loupeRename);
                        ctx.getFileManager().addSequenceOutput(loupeRename, rs.getName() + " 10x Reanalyze Loupe File", "10x Loupe File", rs.getRowId(), null, referenceGenome.getGenomeId(), null);
                    }
                    else
                    {
                        ctx.getLogger().info("loupe file not found: " + loupe.getPath());
                    }

                    File scDir = new File(output, "SC_RNA_REANALYZER_CS");
                    if (scDir.exists())
                    {
                        //NOTE: this will have lots of symlinks, including corrupted ones, which java handles badly
                        new SimpleScriptWrapper(ctx.getLogger()).execute(Arrays.asList("rm", "-Rf", scDir.getPath()));
                    }
                    else
                    {
                        ctx.getLogger().warn("Unable to find folder: " + scDir.getPath());
                    }

                    Set<File> rawDataDirs = CellRangerWrapper.getRawDataDirs(matrix.getParentFile(), false);
                    for (File dir : rawDataDirs)
                    {
                        File dest = new File(out, dir.getName());
                        ctx.getLogger().debug("copying raw data from: " + dir.getPath() + " to " + dest.getPath());
                        FileUtils.copyDirectory(dir, dest);
                    }

                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }
    }
}
