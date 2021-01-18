package org.labkey.sequenceanalysis.pipeline;

import htsjdk.samtools.SAMFileHeader;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractSequenceTaskFactory;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
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
            _taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);

            //move and make sure BAMs are coordinate sorted and indexed
            FileType bamFile = new FileType("bam");
            Map<String, File> bamMap = new HashMap<>();
            for (File f : getTaskHelper().getJob().getInputFiles())
            {
                getJob().getLogger().debug("input file: " + f.getPath());
                bamMap.put(f.getName(), f);
            }


            Map<String, String> params = getJob().getParameters();
            List<Readset> readsets = getPipelineJob().getSequenceSupport().getCachedReadsets();
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

                ReferenceGenome referenceGenome = getPipelineJob().getSequenceSupport().getCachedGenome(o.getInt("library_id"));
                List<PipelineStepCtx<BamProcessingStep>> steps = SequencePipelineService.get().getSteps(getJob(), BamProcessingStep.class);
                File bam = originalFile;
                if (steps.isEmpty())
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

                    for (PipelineStepCtx<BamProcessingStep> stepCtx : steps)
                    {
                        getJob().getLogger().info("performing step: " + stepCtx.getProvider().getLabel());
                        RecordedAction action = new RecordedAction(stepCtx.getProvider().getLabel());
                        action.setStartTime(new Date());
                        _taskHelper.getFileManager().addInput(action, "Input BAM", bam);

                        BamProcessingStep step = stepCtx.getProvider().create(_taskHelper);
                        step.setStepIdx(stepCtx.getStepIdx());
                        getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING: " + stepCtx.getProvider().getLabel().toUpperCase());
                        BamProcessingStep.Output output = step.processBam(rs, bam, referenceGenome, workDir);
                        _taskHelper.getFileManager().addStepOutputs(action, output);

                        if (output.getBAM() != null)
                        {
                            bam = output.getBAM();
                        }
                        else if (step.expectToCreateNewBam())
                        {
                            throw new PipelineJobException("The BAM processing step should have created a new BAM, no BAM was specified. This is possible a coding error on this step");
                        }
                        getJob().getLogger().info("\ttotal alignments in processed BAM: " + SequenceUtil.getAlignmentCount(bam));

                        action.setEndTime(new Date());
                        actions.add(action);
                    }
                }

                //handle inputs
                RecordedAction moveAction = new RecordedAction(ACTION_NAME);
                getTaskHelper().getFileManager().addInput(moveAction, "Input BAM", bam);
                actions.add(moveAction);

                File finalDestination = new File(getTaskHelper().getJob().getAnalysisDirectory(), originalFile.getName());
                if (TaskFileManager.InputFileTreatment.leaveInPlace == getTaskHelper().getFileManager().getInputFileTreatment())
                {
                    if (bam.equals(originalFile))
                    {
                        getJob().getLogger().debug("will leave original BAM in place: " + originalFile.getPath());
                        finalDestination = bam;
                    }
                    else
                    {
                        getJob().getLogger().debug("the BAM was altered during import.  therefore even though leave-in-place was selected, the official copy will reside in the pipeline folder, with the original remaining in place: " + finalDestination.getPath());
                    }
                }
                else
                {
                    getJob().setStatus(PipelineJob.TaskStatus.running, "MOVING BAM");
                    if (!bam.getPath().equals(finalDestination.getPath()))
                    {
                        //note: if BAM is unaltered, copy rather than move to preserve original
                        if (bam.equals(originalFile) && TaskFileManager.InputFileTreatment.none == getTaskHelper().getFileManager().getInputFileTreatment())
                        {
                            FileUtils.copyFile(bam, finalDestination);
                        }
                        else
                        {
                            getJob().getLogger().debug("moving original BAM: " + originalFile.getPath());
                            FileUtils.moveFile(originalFile, finalDestination);
                            File idxOrig = new File(originalFile.getPath() + ".bai");
                            if (idxOrig.exists())
                            {
                                getJob().getLogger().debug("moving BAM index: " + idxOrig.getPath());
                                FileUtils.moveFile(idxOrig, new File(finalDestination.getPath() + ".bai"));
                            }
                        }
                    }
                    getTaskHelper().getFileManager().addOutput(moveAction, SequenceAlignmentTask.FINAL_BAM_ROLE, finalDestination);
                }

                //TODO: we might not want this if LeaveInPlace was selected as the input handling
                //ensure coordinate sorted:
                if (SAMFileHeader.SortOrder.coordinate != SequencePipelineService.get().getBamSortOrder(finalDestination))
                {
                    new SamSorter(getJob().getLogger()).execute(finalDestination, null, SAMFileHeader.SortOrder.coordinate);
                }

                //then index
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
                    getTaskHelper().getFileManager().addPicardMetricsFiles(Arrays.asList(new PipelineStepOutput.PicardMetricsOutput(metricsFile2, finalDestination, rs.getRowId())));
                }

                if (getTaskHelper().getSettings().doCollectWgsMetrics())
                {
                    getJob().getLogger().info("calculating wgs metrics");
                    getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING WGS METRICS");
                    File wgsMetricsFile = new File(finalDestination.getParentFile(), FileUtil.getBaseName(finalDestination) + ".wgs.metrics");
                    CollectWgsMetricsWrapper wgsWrapper = new CollectWgsMetricsWrapper(getJob().getLogger());
                    wgsWrapper.executeCommand(finalDestination, wgsMetricsFile, referenceGenome.getWorkingFastaFile());
                    getTaskHelper().getFileManager().addOutput(metricsAction, "WGS Metrics File", wgsMetricsFile);
                    getTaskHelper().getFileManager().addPicardMetricsFiles(Arrays.asList(new PipelineStepOutput.PicardMetricsOutput(wgsMetricsFile, finalDestination, rs.getRowId())));
                }

                metricsAction.setEndTime(new Date());
                actions.add(metricsAction);

                //delete original, if required
                getJob().setStatus(PipelineJob.TaskStatus.running, "PERFORMING CLEANUP");
                if (TaskFileManager.InputFileTreatment.delete == getTaskHelper().getFileManager().getInputFileTreatment())
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
            getTaskHelper().getFileManager().cleanup(actions);

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

    private AlignmentImportJob getPipelineJob()
    {
        return (AlignmentImportJob)getJob();
    }
}
