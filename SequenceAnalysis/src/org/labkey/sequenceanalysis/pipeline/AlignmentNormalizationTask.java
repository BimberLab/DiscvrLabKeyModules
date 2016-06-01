package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.util.AlignmentSummaryMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.BuildBamIndexWrapper;
import org.labkey.sequenceanalysis.run.util.CollectInsertSizeMetricsWrapper;
import org.labkey.sequenceanalysis.run.util.CollectWgsMetricsWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/5/2014.
 */
public class AlignmentNormalizationTask extends WorkDirectoryTask<AlignmentNormalizationTask.Factory>
{
    private static final String ACTION_NAME = "Normalizing BAM";

    private SequenceTaskHelper _taskHelper;

    protected AlignmentNormalizationTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(AlignmentNormalizationTask.class);
        }

        public String getStatusName()
        {
            return ACTION_NAME.toUpperCase();
        }

        public List<String> getProtocolActionNames()
        {
            List<String> allowableNames = new ArrayList<>();
            for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(BamProcessingStep.class))
            {
                allowableNames.add(provider.getLabel());
            }

            allowableNames.add(ACTION_NAME);
            allowableNames.add(SequenceAlignmentTask.ALIGNMENT_METRICS_ACTIONNAME);

            return allowableNames;
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            AlignmentNormalizationTask task = new AlignmentNormalizationTask(this, job);
            return task;
        }

        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new FileType(".bam"));
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            List<RecordedAction> actions = new ArrayList<>();
            _taskHelper = new SequenceTaskHelper(getJob(), _wd);

            //move and make sure BAMs are indexed
            FileType bamFile = new FileType("bam");
            Map<String, File> bamMap = new HashMap<>();
            for (File f : getTaskHelper().getSupport().getInputFiles())
            {
                getJob().getLogger().debug("input file: " + f.getPath());
                bamMap.put(f.getName(), f);
            }


            Map<String, String> params = getJob().getParameters();
            List<Readset> readsets = getJob().getJobSupport(SequenceAnalysisJobSupport.class).getCachedReadsets();
            int idx = 0;
            for (String key : params.keySet())
            {
                if (!key.startsWith("readset_"))
                {
                    continue;
                }

                JSONObject o = new JSONObject(params.get(key));
                Readset rs = readsets.get(idx);
                idx++;

                File originalFile = bamMap.get(o.getString("fileName"));
                if (originalFile == null)
                {
                    //NOTE: this task will be split, meaning the XML has multiple readsets, even though this task has only one input file
                    getJob().getLogger().debug("skipping readset with file: " + o.getString("fileName"));
                    continue;
                }

                getJob().getLogger().info("processing file: " + originalFile.getPath());
                if (!bamFile.isType(originalFile))
                {
                    getJob().getLogger().error("File is not a BAM file, skipping: " + originalFile.getName());
                    continue;
                }

                ReferenceGenome referenceGenome = getJob().getJobSupport(SequenceAnalysisJobSupport.class).getCachedGenome(o.getInt("library_id"));
                List<PipelineStepProvider<BamProcessingStep>> providers = SequencePipelineService.get().getSteps(getJob(), BamProcessingStep.class);
                File bam = originalFile;
                if (providers.isEmpty())
                {
                    getJob().getLogger().info("No BAM postprocessing is necessary");
                }
                else
                {
                    File workDir = new File(getTaskHelper().getWorkingDirectory(), FileUtil.getBaseName(originalFile.getName()));
                    getJob().getLogger().info("***Starting BAM Post processing");
                    getJob().setStatus(PipelineJob.TaskStatus.running, "BAM POST-PROCESSING");
                    if (!workDir.exists())
                        workDir.mkdirs();

                    for (PipelineStepProvider<BamProcessingStep> provider : providers)
                    {
                        getJob().getLogger().info("performing step: " + provider.getLabel());
                        RecordedAction action = new RecordedAction(provider.getLabel());
                        action.setStartTime(new Date());
                        _taskHelper.getFileManager().addInput(action, "Input BAM", bam);

                        BamProcessingStep step = provider.create(_taskHelper);
                        getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING: " + provider.getLabel().toUpperCase());
                        BamProcessingStep.Output output = step.processBam(rs, bam, referenceGenome, workDir);
                        _taskHelper.getFileManager().addStepOutputs(action, output);

                        if (output.getBAM() != null)
                        {
                            bam = output.getBAM();
                        }
                        getJob().getLogger().info("\ttotal alignments in processed BAM: " + SequenceUtil.getAlignmentCount(bam));

                        action.setEndTime(new Date());
                        actions.add(action);
                    }
                }

                //first copy the end product to the final location
                getJob().setStatus(PipelineJob.TaskStatus.running, "MOVING BAM");
                File finalDestination = new File(getTaskHelper().getSupport().getAnalysisDirectory(), originalFile.getName());
                RecordedAction moveAction = new RecordedAction(ACTION_NAME);
                getTaskHelper().getFileManager().addInput(moveAction, "Input BAM", bam);
                actions.add(moveAction);

                if (!bam.getPath().equals(finalDestination.getPath()))
                {
                    //note: if BAM is unaltered, copy rather than move to preserve original
                    if (bam.equals(originalFile) && "none".equals(getTaskHelper().getFileManager().getInputfileTreatment()))
                    {
                        FileUtils.copyFile(bam, finalDestination);
                    }
                    else
                    {
                        FileUtils.moveFile(bam, finalDestination);
                    }
                }
                getTaskHelper().getFileManager().addOutput(moveAction, SequenceAlignmentTask.FINAL_BAM_ROLE, finalDestination);

                getJob().setStatus(PipelineJob.TaskStatus.running, "INDEXING BAM");
                File finalIndexFile = new File(finalDestination.getPath() + ".bai");
                if (!finalIndexFile.exists())
                {
                    new BuildBamIndexWrapper(getJob().getLogger()).executeCommand(finalDestination);
                }

                getTaskHelper().getFileManager().addOutput(moveAction, SequenceAlignmentTask.FINAL_BAM_INDEX_ROLE, finalIndexFile);

                //generate alignment metrics
                RecordedAction metricsAction = new RecordedAction(SequenceAlignmentTask.ALIGNMENT_METRICS_ACTIONNAME);
                metricsAction.setStartTime(new Date());

                getJob().getLogger().info("calculating alignment metrics");
                getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING ALIGNMENT SUMMARY METRICS");
                File metricsFile = new File(finalDestination.getParentFile(), FileUtil.getBaseName(finalDestination) + ".summary.metrics");
                new AlignmentSummaryMetricsWrapper(getJob().getLogger()).executeCommand(finalDestination, referenceGenome.getWorkingFastaFile(), metricsFile);
                getTaskHelper().getFileManager().addInput(metricsAction, "BAM File", finalDestination);
                getTaskHelper().getFileManager().addOutput(metricsAction, "Summary Metrics File", metricsFile);
                getTaskHelper().getFileManager().addPicardMetricsFiles(Arrays.asList(new PipelineStepOutput.PicardMetricsOutput(metricsFile, finalDestination, rs.getRowId())));

                //and insert size metrics
                getJob().getLogger().info("calculating insert size metrics");
                getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING INSERT SIZE METRICS");
                File metricsFile2 = new File(finalDestination.getParentFile(), FileUtil.getBaseName(finalDestination) + ".insertsize.metrics");
                File metricsHistogram = new File(finalDestination.getParentFile(), FileUtil.getBaseName(finalDestination) + ".insertsize.metrics.pdf");
                if (new CollectInsertSizeMetricsWrapper(getJob().getLogger()).executeCommand(finalDestination, metricsFile2, metricsHistogram) != null)
                {
                    getTaskHelper().getFileManager().addOutput(metricsAction, "Insert Size Metrics File", metricsFile2);
                    getTaskHelper().getFileManager().addOutput(metricsAction, "Insert Size  Metrics Histogram", metricsHistogram);
                    getTaskHelper().getFileManager().addPicardMetricsFiles(Arrays.asList(new PipelineStepOutput.PicardMetricsOutput(metricsFile, finalDestination, rs.getRowId())));
                }

                if (getTaskHelper().getSettings().doCollectWgsMetrics())
                {
                    getJob().getLogger().info("calculating wgs metrics");
                    getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING WGS METRICS");
                    File wgsMetricsFile = new File(finalDestination.getParentFile(), FileUtil.getBaseName(finalDestination) + ".wgs.metrics");
                    CollectWgsMetricsWrapper wgsWrapper = new CollectWgsMetricsWrapper(getJob().getLogger());
                    wgsWrapper.executeCommand(finalDestination, wgsMetricsFile, referenceGenome.getWorkingFastaFile());
                    getTaskHelper().getFileManager().addOutput(metricsAction, "WGS Metrics File", wgsMetricsFile);
                    getTaskHelper().getFileManager().addPicardMetricsFiles(Arrays.asList(new PipelineStepOutput.PicardMetricsOutput(metricsFile, finalDestination, rs.getRowId())));
                }

                metricsAction.setEndTime(new Date());
                actions.add(metricsAction);

                //delete original, if required
                getJob().setStatus(PipelineJob.TaskStatus.running, "PERFORMING CLEANUP");
                String handling = getTaskHelper().getFileManager().getInputfileTreatment();
                if ("delete".equals(handling))
                {
                    getJob().getLogger().info("deleting original BAM: " + originalFile.getPath());
                    originalFile.delete();

                    File indexFile = new File(originalFile.getPath() + ".bai");
                    if (indexFile.exists())
                    {
                        getJob().getLogger().info("BAM index exists, deleting");
                        indexFile.delete();
                    }
                }
            }

            getTaskHelper().getFileManager().deleteIntermediateFiles();
            getTaskHelper().getFileManager().cleanup();

            return new RecordedActionSet(actions);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public SequenceTaskHelper getTaskHelper()
    {
        return _taskHelper;
    }
}
