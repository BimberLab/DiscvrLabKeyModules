package org.labkey.sequenceanalysis.analysis;

import org.json.JSONObject;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.sequenceanalysis.pipeline.PicardMetricsUtil;
import org.labkey.sequenceanalysis.pipeline.SequenceOutputHandlerJob;
import org.labkey.sequenceanalysis.run.util.AlignmentSummaryMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.CollectInsertSizeMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.CollectWgsMetricsWithNonZeroCoverageWrapper;
import org.labkey.sequenceanalysis.run.util.CollectWgsMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.MarkDuplicatesWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 9/8/2014.
 */
public class PicardAlignmentMetricsHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private final FileType _bamOrCramFileType = SequenceUtil.FILETYPE.bamOrCram.getFileType();

    public PicardAlignmentMetricsHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Picard Metrics", "This will run the select Picard tools metrics on the input BAMs, storing these values in the database.", null, Arrays.asList(
                ToolParameterDescriptor.create("collectSummary", "Run Alignment Summary Metrics", "If checked, Picard CollectAlignmentSummaryMetrics will be run", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, false),
                ToolParameterDescriptor.create("collectInsertSize", "Run Insert Size Metrics", "If checked, Picard CollectInsertSizeMetrics will be run", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, false),
                ToolParameterDescriptor.create("collectWgs", "Run WGS Metrics", "If checked, Picard CollectWgsMetrics will be run", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, false),
                ToolParameterDescriptor.create("collectWgsNonZero", "Run WGS Metrics Over Non-Zero Coverage", "If checked, Picard CollectWgsMetricsWithNonZeroCoverage will be run", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("markDuplicates", "Run MarkDuplicates", "If checked, Picard CollectWgsMetricsWithNonZeroCoverage will be run", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
        ));
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
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile o : inputFiles)
            {
                if (o.getLibrary_id() != null)
                {
                    ctx.getSequenceSupport().cacheGenome(SequenceAnalysisService.get().getReferenceGenome(o.getLibrary_id(), ctx.getJob().getUser()));
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
            if (!(job instanceof SequenceOutputHandlerJob shj))
            {
                throw new IllegalStateException("Expected job to be a SequenceOutputHandlerJob");
            }

            boolean collectSummary = shj.getParameterJson().optBoolean("collectSummary", false);
            boolean collectInsertSize = shj.getParameterJson().optBoolean("collectInsertSize", false);
            boolean collectWgs = shj.getParameterJson().optBoolean("collectWgs", false);
            boolean collectWgsNonZero = shj.getParameterJson().optBoolean("collectWgsNonZero", false);
            boolean runMarkDuplicates = shj.getParameterJson().optBoolean("markDuplicates", false);

            for (SequenceOutputFile o : inputs)
            {
                Integer analysisId = o.getAnalysis_id();
                if (analysisId == null)
                {
                    job.getLogger().warn("no analysis Id for file, attempting to find this job: " + o.getName());
                    PipelineStatusFile sf = PipelineService.get().getStatusFile(job.getJobGUID());

                    TableSelector ts = new TableSelector(QueryService.get().getUserSchema(job.getUser(), job.getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_ANALYSES), PageFlowUtil.set("rowid"), new SimpleFilter(FieldKey.fromString("runid/JobId"), sf.getRowId()), null);
                    if (ts.exists())
                    {
                        analysisId = ts.getObject(Integer.class);
                    }
                    else
                    {
                        throw new IllegalStateException("Unable to find analysis for the input for this job");
                    }
                }

                if (o.getLibrary_id() == null)
                {
                    job.getLogger().warn("no genome associated with file: " + o.getName());
                    continue;
                }

                AnalysisModel m = AnalysisModelImpl.getFromDb(analysisId, job.getUser());
                if (m != null)
                {
                    job.getLogger().warn("processing analysis: " + m.getRowId());
                    File outputDir = ((SequenceOutputHandlerJob)job).getWebserverDir(false);
                    List<File> metricsFiles = new ArrayList<>();

                    File mf = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".summary.metrics");
                    if (mf.exists())
                    {
                        metricsFiles.add(mf);
                    }
                    else if (collectSummary)
                    {
                        throw new PipelineJobException("Missing file: " + mf.getPath());
                    }

                    File mf2 = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".insertsize.metrics");
                    if (mf2.exists())
                    {
                        metricsFiles.add(mf2);
                    }
                    else if (collectInsertSize)
                    {
                        // This output is only created for paired data:
                        if (o.getReadset() != null)
                        {
                            Readset rs = SequenceAnalysisService.get().getReadset(o.getReadset(), job.getUser());
                            if (rs.getReadData().stream().filter(rd -> rd.getFileId2() != null).count() > 0)
                            {
                                throw new PipelineJobException("Missing file: " + mf2.getPath());
                            }
                        }
                    }

                    File mf3 = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".wgs.metrics");
                    if (mf3.exists())
                    {
                        metricsFiles.add(mf3);
                    }
                    else if (collectWgs)
                    {
                        throw new PipelineJobException("Missing file: " + mf3.getPath());
                    }

                    File mf4 = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".wgsNonZero.metrics");
                    if (mf4.exists())
                    {
                        metricsFiles.add(mf4);
                    }
                    else if (collectWgsNonZero)
                    {
                        throw new PipelineJobException("Missing file: " + mf4.getPath());
                    }

                    File mf5 = new MarkDuplicatesWrapper(job.getLogger()).getMetricsFile(o.getFile());
                    if (mf5.exists())
                    {
                        metricsFiles.add(mf5);
                    }
                    else if (runMarkDuplicates)
                    {
                        throw new PipelineJobException("Missing file: " + mf5.getPath());
                    }

                    TableInfo ti = SequenceAnalysisManager.get().getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS);
                    for (File f : metricsFiles)
                    {
                        List<Map<String, Object>> lines = PicardMetricsUtil.processFile(f, job.getLogger());
                        for (Map<String, Object> row : lines)
                        {
                            row.put("container", o.getContainer());
                            row.put("createdby", job.getUser().getUserId());
                            row.put("created", new Date());
                            row.put("readset", m.getReadset());
                            row.put("analysis_id", m.getRowId());
                            row.put("dataid", m.getAlignmentFile());

                            Table.insert(job.getUser(), ti, row);
                        }
                    }
                }
                else
                {
                    job.getLogger().warn("Analysis Id " + o.getAnalysis_id() + " not found for file: " + o.getName());
                }
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            JSONObject params = ctx.getParams();

            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            boolean collectSummary = params.optBoolean("collectSummary", false);
            boolean collectInsertSize = params.optBoolean("collectInsertSize", false);
            boolean collectWgs = params.optBoolean("collectWgs", false);
            boolean collectWgsNonZero = params.optBoolean("collectWgsNonZero", false);
            boolean runMarkDuplicates = params.optBoolean("markDuplicates", false);

            int i = 1;
            for (SequenceOutputFile o : inputFiles)
            {
                job.getLogger().info("processing file " + i + " of " + inputFiles.size());
                i++;

                if (o.getLibrary_id() == null)
                {
                    job.getLogger().warn("no genome associated with file: " + o.getName());
                    continue;
                }

                if (collectSummary)
                {
                    job.getLogger().info("calculating summary metrics");
                    job.setStatus(PipelineJob.TaskStatus.running, "CALCULATING SUMMARY METRICS");
                    File metricsFile = new File(ctx.getOutputDir(), FileUtil.getBaseName(o.getFile()) + ".summary.metrics");
                    AlignmentSummaryMetricsWrapper wrapper = new AlignmentSummaryMetricsWrapper(job.getLogger());
                    wrapper.executeCommand(o.getFile(), ctx.getSequenceSupport().getCachedGenome(o.getLibrary_id()).getWorkingFastaFile(), metricsFile);
                }

                if (collectWgs)
                {
                    job.getLogger().info("calculating wgs metrics");
                    job.setStatus(PipelineJob.TaskStatus.running, "CALCULATING WGS METRICS");
                    File wgsMetricsFile = new File(ctx.getOutputDir(), FileUtil.getBaseName(o.getFile()) + ".wgs.metrics");
                    CollectWgsMetricsWrapper wgsWrapper = new CollectWgsMetricsWrapper(job.getLogger());
                    wgsWrapper.executeCommand(o.getFile(), wgsMetricsFile, ctx.getSequenceSupport().getCachedGenome(o.getLibrary_id()).getWorkingFastaFile());
                }

                if (collectWgsNonZero)
                {
                    job.getLogger().info("calculating wgs metrics over non zero positions");
                    job.setStatus(PipelineJob.TaskStatus.running, "CALCULATING WGS METRICS");
                    File wgsMetricsFile = new File(ctx.getOutputDir(), FileUtil.getBaseName(o.getFile()) + ".wgsNonZero.metrics");
                    CollectWgsMetricsWithNonZeroCoverageWrapper wgsWrapper = new CollectWgsMetricsWithNonZeroCoverageWrapper(job.getLogger());
                    wgsWrapper.executeCommand(o.getFile(), wgsMetricsFile, ctx.getSequenceSupport().getCachedGenome(o.getLibrary_id()).getWorkingFastaFile());
                }

                if (collectInsertSize)
                {
                    job.getLogger().info("calculating insert size metrics");
                    job.setStatus(PipelineJob.TaskStatus.running, "CALCULATING INSERT SIZE METRICS");
                    File metricsFile = new File(ctx.getOutputDir(), FileUtil.getBaseName(o.getFile()) + ".insertsize.metrics");
                    File metricsHistogram = new File(ctx.getOutputDir(), FileUtil.getBaseName(o.getFile()) + ".insertsize.metrics.pdf");
                    CollectInsertSizeMetricsWrapper wrapper = new CollectInsertSizeMetricsWrapper(job.getLogger());
                    wrapper.executeCommand(o.getFile(), metricsFile, metricsHistogram, ctx.getSequenceSupport().getCachedGenome(o.getLibrary_id()).getWorkingFastaFile());
                }

                if (runMarkDuplicates)
                {
                    job.getLogger().info("running MarkDuplicates");
                    job.setStatus(PipelineJob.TaskStatus.running, "RUNNING MARKDUPLICATES");
                    MarkDuplicatesWrapper wrapper = new MarkDuplicatesWrapper(job.getLogger());
                    File metricsFile = wrapper.getMetricsFile(o.getFile());
                    File tempBam = new File(ctx.getOutputDir(), FileUtil.getBaseName(o.getFile()) + ".markDuplicates.bam");
                    ctx.getFileManager().addIntermediateFile(tempBam);
                    ctx.getFileManager().addIntermediateFile(SequenceUtil.getExpectedIndex(tempBam));

                    if (tempBam.exists())
                    {
                        tempBam.delete();
                    }

                    wrapper.executeCommand(o.getFile(), tempBam, null);
                    if (!metricsFile.exists())
                    {
                        throw new PipelineJobException("Unable to find file: " + metricsFile);
                    }
                }
            }

            action.setEndTime(new Date());
            ctx.addActions(action);
        }
    }
}
