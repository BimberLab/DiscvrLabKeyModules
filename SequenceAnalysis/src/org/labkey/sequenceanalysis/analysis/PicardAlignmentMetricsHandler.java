package org.labkey.sequenceanalysis.analysis;

import org.json.JSONObject;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.sequenceanalysis.pipeline.PicardMetricsUtil;
import org.labkey.sequenceanalysis.run.util.AlignmentSummaryMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.CollectInsertSizeMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.CollectWgsMetricsWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 9/8/2014.
 */
public class PicardAlignmentMetricsHandler extends AbstractParameterizedOutputHandler
{
    private FileType _bamFileType = new FileType("bam", false);

    public PicardAlignmentMetricsHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Picard Metrics", "This will run the select Picard tools metrics on the input BAMs, storing these values in the database.", null, Arrays.asList(
                ToolParameterDescriptor.create("collectSummary", "Run Alignment Summary Metrics", "If checked, Picard CollectAlignmentSummaryMetrics will be run", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, 500),
                ToolParameterDescriptor.create("collectInsertSize", "Run Insert Size Metrics", "If checked, Picard CollectInsertSizeMetrics will be run", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, 500),
                ToolParameterDescriptor.create("collectWgs", "Run WGS Metrics", "If checked, Picard CollectWgsMetrics will be run", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, 500)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _bamFileType.isType(o.getFile());
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
    }

    @Override
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile o : inputFiles)
            {
                if (o.getLibrary_id() != null)
                {
                    support.cacheGenome(SequenceAnalysisService.get().getReferenceGenome(o.getLibrary_id(), job.getUser()));
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile o : inputFiles)
            {
                if (o.getAnalysis_id() == null)
                {
                    job.getLogger().warn("no analysis Id for file: " + o.getName());
                    continue;
                }

                if (o.getLibrary_id() == null)
                {
                    job.getLogger().warn("no genome associated with file: " + o.getName());
                    continue;
                }

                AnalysisModel m = AnalysisModelImpl.getFromDb(o.getAnalysis_id(), job.getUser());
                if (m != null)
                {
                    job.getLogger().warn("processing analysis: " + m.getRowId());
                    List<File> metricsFiles = new ArrayList<>();

                    RecordedAction action = new RecordedAction(getName());
                    action.addInput(o.getFile(), "Input BAM");

                    File mf = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".summary.metrics");
                    if (mf.exists())
                    {
                        action.addOutput(mf, "Alignment Summary Metrics", false);
                        metricsFiles.add(mf);
                    }

                    File mf2 = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".insertsize.metrics");
                    if (mf2.exists())
                    {
                        action.addOutput(mf2, "InsertSize Metrics", false);
                        metricsFiles.add(mf2);
                    }

                    File mf3 = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".wgs.metrics");
                    if (mf3.exists())
                    {
                        action.addOutput(mf3, "WGS Metrics", false);
                        metricsFiles.add(mf3);
                    }

                    TableInfo ti = SequenceAnalysisManager.get().getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS);
                    for (File f : metricsFiles)
                    {
                        List<Map<String, Object>> lines = PicardMetricsUtil.processFile(f, job.getLogger());
                        for (Map<String, Object> row : lines)
                        {
                            row.put("container", job.getContainer().getId());
                            row.put("createdby", job.getUser().getUserId());
                            row.put("created", new Date());
                            row.put("readset", m.getReadset());
                            row.put("analysis_id", m.getRowId());
                            row.put("dataid", m.getAlignmentFile());

                            Table.insert(job.getUser(), ti, row);
                        }
                    }

                    actions.add(action);
                }
                else
                {
                    job.getLogger().warn("Analysis Id " + o.getAnalysis_id() + " not found for file: " + o.getName());
                }
            }
        }

        @Override
        public void processFilesRemote(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            boolean collectSummary = params.containsKey("collectSummary") && params.get("collectSummary") != null ? "on".equals(params.getString("collectSummary")) : false;
            boolean collectInsertSize = params.containsKey("collectInsertSize") && params.get("collectInsertSize") != null ? "on".equals(params.getString("collectInsertSize")) : false;
            boolean collectWgs = params.containsKey("collectWgs") && params.get("collectWgs") != null ? "on".equals(params.getString("collectWgs")) : false;

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
                    File metricsFile = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".summary.metrics");
                    AlignmentSummaryMetricsWrapper wrapper = new AlignmentSummaryMetricsWrapper(job.getLogger());
                    wrapper.executeCommand(o.getFile(), support.getCachedGenome(o.getLibrary_id()).getWorkingFastaFile(), metricsFile);
                }

                if (collectWgs)
                {
                    job.getLogger().info("calculating wgs metrics");
                    job.setStatus(PipelineJob.TaskStatus.running, "CALCULATING WGS METRICS");
                    File wgsMetricsFile = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".wgs.metrics");
                    CollectWgsMetricsWrapper wgsWrapper = new CollectWgsMetricsWrapper(job.getLogger());
                    wgsWrapper.executeCommand(o.getFile(), wgsMetricsFile, support.getCachedGenome(o.getLibrary_id()).getWorkingFastaFile());
                }

                if (collectInsertSize)
                {
                    job.getLogger().info("calculating insert size metrics");
                    job.setStatus(PipelineJob.TaskStatus.running, "CALCULATING INSERT SIZE METRICS");
                    File metricsFile = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".insertsize.metrics");
                    File metricsHistogram = new File(outputDir, FileUtil.getBaseName(o.getFile()) + ".insertsize.metrics.pdf");
                    CollectInsertSizeMetricsWrapper wrapper = new CollectInsertSizeMetricsWrapper(job.getLogger());
                    wrapper.executeCommand(o.getFile(), metricsFile, metricsHistogram);
                }
            }

            action.setEndTime(new Date());
            actions.add(action);
        }
    }
}
