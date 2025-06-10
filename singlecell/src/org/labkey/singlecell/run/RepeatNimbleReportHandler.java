package org.labkey.singlecell.run;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.DefaultPipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.singlecell.SingleCellModule;
import org.labkey.singlecell.SingleCellSchema;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RepeatNimbleReportHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public RepeatNimbleReportHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Re-run Nimble Report", "This will re-run nimble report and nimble plot for the selected run and replace the original files in-place.", null, Arrays.asList(
                        ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                            put("checked", true);
                        }}, true)
                )
        );
    }

    private static final FileType _nimbleResultsGz = new FileType(".txt", FileType.gzSupportLevel.SUPPORT_GZ);

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && o.getFile().getName().startsWith("reportResults.") && _nimbleResultsGz.isType(o.getFile());
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

    private static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        private File getAlignmentResults(File reportResults)
        {
            return new File(reportResults.getParentFile(), reportResults.getName().replaceAll("reportResults", "alignResults") + ".gz");
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineStepOutput output = new DefaultPipelineStepOutput();

            for (SequenceOutputFile so : inputFiles)
            {
                // This is the prior report results:
                File alignmentFile = getAlignmentResults(so.getFile());
                if (!alignmentFile.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + alignmentFile.getPath());
                }

                // This will update these files in-place:
                File reportFile = NimbleHelper.runNimbleReport(alignmentFile, so.getLibrary_id(), output, ctx);
                if (!reportFile.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + reportFile.getPath());
                }

                File htmlFile = NimbleHelper.getReportHtmlFileFromResults(reportFile);
                if (!htmlFile.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + htmlFile.getPath());
                }

                // Replace the originals:
                try
                {
                    File targetHtml = new File(so.getFile().getParentFile(), htmlFile.getName());
                    if (targetHtml.exists())
                    {
                        targetHtml.delete();
                    }
                    FileUtils.moveFile(htmlFile, targetHtml);

                    File targetReport = new File(so.getFile().getParentFile(), reportFile.getName());
                    if (targetReport.exists())
                    {
                        targetReport.delete();
                    }
                    else
                    {
                        ctx.getLogger().error("Expected report file to exist: " + targetReport.getPath());
                    }
                    FileUtils.moveFile(reportFile, targetReport);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            ctx.getFileManager().addIntermediateFiles(output.getIntermediateFiles());
        }

        @Override
        public void complete(JobContext ctx, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated) throws PipelineJobException
        {
            // Because the plot output was added later, re-create this if it doesnt exist:
            for (SequenceOutputFile so : inputs)
            {
                File plotFile = NimbleHelper.getReportHtmlFileFromResults(so.getFile());

                TableInfo ti = QueryService.get().getUserSchema(ctx.getJob().getUser(), so.getContainerObj(), SingleCellSchema.SEQUENCE_SCHEMA_NAME).getTable("outputfiles");
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("category"), "").addCondition(FieldKey.fromString("dataid/dataFileUrl"), plotFile.toURI().toString());
                TableSelector ts = new TableSelector(ti, filter, null);
                if (!ts.exists())
                {
                    ExpData expData = ExperimentService.get().getExpDataByURL(plotFile, so.getContainerObj());
                    if (expData == null)
                    {
                        expData = ExperimentService.get().createData(so.getContainerObj(), new DataType("Nimble Results"));
                        expData.setDataFileURI(plotFile.toURI());
                        expData.setName(plotFile.getName());
                        expData.save(ctx.getJob().getUser());
                    }

                    Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
                    toInsert.put("name", so.getName().replaceAll("nimble results", "nimble report"));
                    toInsert.put("category", NimbleHelper.NIMBLE_REPORT_CATEGORY);
                    toInsert.put("description", so.getDescription());
                    toInsert.put("dataid", expData.getRowId());
                    toInsert.put("library_id", so.getLibrary_id());
                    toInsert.put("runid", so.getRunId());
                    toInsert.put("analysis_id", so.getAnalysis_id());

                    try
                    {
                        ti.getUpdateService().insertRows(ctx.getJob().getUser(), so.getContainerObj(), Collections.singletonList(toInsert), new BatchValidationException(), null, null);
                    }
                    catch (SQLException | BatchValidationException | QueryUpdateServiceException | DuplicateKeyException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
                else
                {
                    ctx.getLogger().debug("Plot file output exists, will not re-create");
                }
            }
        }
    }
}
