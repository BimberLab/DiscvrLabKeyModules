/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis.pipeline;

import htsjdk.samtools.metrics.MetricsFile;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.sequenceanalysis.run.util.BamMetricsRunner;
import org.labkey.sequenceanalysis.util.FastqUtils;
import picard.analysis.AlignmentSummaryMetrics;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 9/23/12
 * Time: 9:40 AM
 */
public class SequenceAnalysisTask extends WorkDirectoryTask<SequenceAnalysisTask.Factory>
{
    protected SequenceAnalysisTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(SequenceAnalysisTask.class);
        }

        public String getStatusName()
        {
            return "PERFORMING ANALYSIS";
        }

        public List<String> getProtocolActionNames()
        {
            List<String> allowableNames = new ArrayList<>();
            for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(AnalysisStep.class))
            {
                allowableNames.add(provider.getLabel());
            }

            return allowableNames;
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            SequenceAnalysisTask task = new SequenceAnalysisTask(this, job);
            setJoin(true);
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

    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        SequenceTaskHelper taskHelper = new SequenceTaskHelper(job, _wd);

        List<RecordedAction> actions = new ArrayList<>();

        job.getLogger().info("Processing Alignments");

        //first validate all analysis records before actually creating any DB records
        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());
        ExpRun run = ExperimentService.get().getExpRun(runId);
        List<AnalysisModelImpl> analysisModels = new ArrayList<>();

        if (SequenceTaskHelper.isAlignmentUsed(getJob()))
        {
            for (SequenceReadsetImpl rs : taskHelper.getSettings().getReadsets(getJob().getJobSupport(SequenceAnalysisJob.class)))
            {
                String basename = rs.getLegalFileName();

                AnalysisModelImpl model = new AnalysisModelImpl();
                model.setContainer(getJob().getContainer().getId());
                model.setCreatedby(getJob().getUser().getUserId());
                model.setCreated(new Date());
                model.setModifiedby(getJob().getUser().getUserId());
                model.setModified(new Date());
                model.setDescription(taskHelper.getSettings().getProtocolDescription());
                model.setRunId(runId);
                model.setReadset(rs.getReadsetId());

                //find BAM
                List<? extends ExpData> datas = run.getInputDatas(SequenceAlignmentTask.FINAL_BAM_ROLE, ExpProtocol.ApplicationType.ExperimentRunOutput);
                if (datas.size() > 0)
                {
                    boolean found = false;
                    for (ExpData d : datas)
                    {
                        if (basename.equals(FileUtil.getBaseName(d.getFile())))
                        {
                            if (found)
                            {
                                getJob().getLogger().warn("ERROR: More than 1 matching BAM found for basename: " + basename);
                                getJob().getLogger().warn("BAM was: " + d.getFile().getPath());
                            }

                            if (!d.getFile().exists())
                                throw new PipelineJobException("Unable to find file: " + d.getFile().getPath());

                            model.setAlignmentFile(d.getRowId());
                            found = true;
                        }
                    }
                }

                if (model.getAlignmentFile() == null)
                {
                    getJob().getLogger().warn("Unable to find BAM for run: " + run.getRowId() + ". Expected file beginning with: " + basename);
                    getJob().getLogger().info("Total BAMs found : " + datas.size());
                    for (ExpData d : datas)
                    {
                        getJob().getLogger().info("\t" + d.getFile().getPath());
                    }
                    continue;
                }

                //reference
                ReferenceGenome referenceGenome = taskHelper.getSequenceSupport().getReferenceGenome();
                if (referenceGenome == null)
                {
                    throw new PipelineJobException("unable to find cached reference genome");
                }

                model.setLibraryId(referenceGenome.getGenomeId());
                if (referenceGenome.getFastaExpDataId() != null)
                {
                    model.setReferenceLibrary(referenceGenome.getFastaExpDataId());
                }
                else
                {
                    List<? extends ExpData> fastaDatas = run.getInputDatas(ReferenceLibraryTask.REFERENCE_DB_FASTA, null);
                    if (fastaDatas.size() > 0)
                    {
                        for (ExpData d : fastaDatas)
                        {
                            if (d.getFile() == null)
                            {
                                job.getLogger().debug("No file found for ExpData: " + d.getRowId());
                            }
                            else if (d.getFile().exists())
                            {
                                model.setReferenceLibrary(d.getRowId());
                                break;
                            }
                            else
                            {
                                job.getLogger().debug("File does not exist: " + d.getFile().getPath());
                            }
                        }
                    }
                }

                if (model.getReferenceLibrary() == null)
                {
                    getJob().getLogger().error("Unable to find reference FASTA for run: " + run.getRowId());
                    continue;
                }

                //input FASTQs
                datas = run.getInputDatas(SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, ExpProtocol.ApplicationType.ProtocolApplication);
                if (datas.size() > 0)
                {
                    for (ExpData d : datas)
                    {
                        if (d.getFile() == null)
                        {
                            getJob().getLogger().debug("No file found for data: " + d.getRowId() + " / run: " + run.getRowId());
                            continue;
                        }
                    }
                }

                analysisModels.add(model);
            }
        }

        if (analysisModels.size() == 0)
        {
            getJob().getLogger().info("No analyses were created");
        }

        int i = 0;
        List<AnalysisStep.Output> outputs = new ArrayList<>();
        List<PipelineStepProvider<AnalysisStep>> providers = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
        if (providers.isEmpty())
        {
            getJob().getLogger().info("no analyses were selected");
        }

        for (AnalysisModelImpl model : analysisModels)
        {
            try
            {
                i++;
                getJob().getLogger().info("processing BAM " + i + " of " + analysisModels.size());

                File bam = ExperimentService.get().getExpData(model.getAlignmentFile()).getFile();
                if (bam == null)
                {
                    getJob().getLogger().error("unable to find BAM, skipping");
                    continue;
                }

                File refDB = ExperimentService.get().getExpData(model.getReferenceLibrary()).getFile();
                if (refDB == null)
                {
                    getJob().getLogger().error("unable to find reference fasta, skipping");
                    continue;
                }

                getJob().getLogger().info("creating analysis record for BAM: " + bam.getName());
                TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
                Table.insert(getJob().getUser(), ti, model);

                SequenceOutputFile so = new SequenceOutputFile();
                so.setName(bam.getName());
                so.setCategory("Alignment");
                so.setAnalysis_id(model.getAnalysisId());
                so.setReadset(model.getReadset());
                so.setLibrary_id(model.getLibrary_Id());
                ExpData d = ExperimentService.get().getExpDataByURL(bam, getJob().getContainer());
                if (d == null)
                {
                    getJob().getLogger().info("creating ExpData for file: " + bam.getPath());

                    d = ExperimentService.get().createData(getJob().getContainer(), new DataType("Alignment"));
                    d.setDataFileURI(bam.toURI());
                    d.setName(bam.getName());
                    d.save(getJob().getUser());
                }
                so.setDataId(d.getRowId());
                so.setContainer(getJob().getContainerId());

                Table.insert(getJob().getUser(), SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), so);

                addMetricsForAnalysis(model);

                if (!providers.isEmpty())
                {
                    outputs.addAll(runAnalysesLocal(actions, model, bam, refDB, providers, taskHelper));
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        //List<AnalysisModel> models = new ArrayList<>();
        //models.addAll(analysisModels);

        taskHelper.getFileManager().createSequenceOutputRecords();

        return new RecordedActionSet();
    }

    private void addMetricsForAnalysis(AnalysisModel model) throws SQLException, PipelineJobException
    {
        if (model.getAlignmentFile() != null && model.getReferenceLibraryFile() != null && model.getReferenceLibraryFile().exists())
        {
            ExpData d = ExperimentService.get().getExpData(model.getAlignmentFile());
            if (!d.getFile().exists())
            {
                return;
            }

            File fai = new File(model.getReferenceLibraryFile().getPath() + ".fai");
            if (!fai.exists())
            {
                getJob().getLogger().error("missing FASTA index, cannot calculate BAM metrics");
            }

            getJob().getLogger().info("calculating metrics for BAM using Picard AlignmentSummaryMetricsCollector: " + d.getFile().getName());

            MetricsFile metricsFile = new BamMetricsRunner(getJob().getLogger()).addMetricsForFile(d.getFile(), model.getReferenceLibraryFile());
            List<AlignmentSummaryMetrics> metrics = metricsFile.getMetrics();
            TableInfo ti = SequenceAnalysisManager.get().getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS);
            for (AlignmentSummaryMetrics m : metrics)
            {
                Map<String, Object> row = new HashMap<>();
                row.put("Avg Sequence Length", m.MEAN_READ_LENGTH);
                row.put("%Reads Aligned In Pairs", m.PCT_READS_ALIGNED_IN_PAIRS * 100);
                row.put("Total Sequences", m.TOTAL_READS);
                row.put("Total Sequences Passed Filter", m.PF_READS);
                row.put("Reads Aligned", m.PF_READS_ALIGNED);
                row.put("%Reads Aligned", m.PCT_PF_READS_ALIGNED * 100);

                for (String metricName : row.keySet())
                {
                    Map<String, Object> r = new HashMap<>();
                    r.put("category", m.CATEGORY);
                    r.put("metricname", metricName);
                    r.put("metricvalue", row.get(metricName));
                    r.put("dataid", d.getRowId());
                    r.put("analysis_id", model.getAnalysisId());
                    r.put("readset", model.getReadset());
                    r.put("container", getJob().getContainer().getEntityId());
                    r.put("createdby", getJob().getUser().getUserId());

                    Table.insert(getJob().getUser(), ti, r);
                }
            }
        }
    }

    private void addFastqFileMetrics(int analysisId, int fileId) throws SQLException
    {
        ExpData d = ExperimentService.get().getExpData(fileId);
        if (!d.getFile().exists())
        {
            getJob().getLogger().info("Input file does not exist, so no metrics will be gathered: " + d.getFile().getPath());
            return;
        }

        Map<String, Object> metricsMap = FastqUtils.getQualityMetrics(d.getFile(), getJob().getLogger());
        for (String metricName : metricsMap.keySet())
        {
            Map<String, Object> r = new HashMap<>();
            r.put("metricname", metricName);
            r.put("metricvalue", metricsMap.get(metricName));
            r.put("dataid", d.getRowId());
            r.put("analysis_id", analysisId);
            r.put("container", getJob().getContainer());
            r.put("createdby", getJob().getUser().getUserId());

            Table.insert(getJob().getUser(), SequenceAnalysisManager.get().getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS), r);
        }
    }

    public static List<AnalysisStep.Output> runAnalysesLocal(List<RecordedAction> actions, AnalysisModel model, File inputBam, File refFasta, List<PipelineStepProvider<AnalysisStep>> providers, SequenceTaskHelper taskHelper) throws PipelineJobException
    {
        List<AnalysisStep.Output> ret = new ArrayList<>();
        for (PipelineStepProvider<AnalysisStep> provider : providers)
        {
            taskHelper.getJob().getLogger().info("Running " + provider.getLabel() + " for analysis: " + model.getRowId());
            taskHelper.getJob().getLogger().info("\tUsing alignment: " + inputBam.getPath());

            RecordedAction action = new RecordedAction(provider.getLabel());
            taskHelper.getFileManager().addInput(action, "Input BAM File", inputBam);
            taskHelper.getFileManager().addInput(action, "Reference DB FASTA", refFasta);
            //taskHelper.getFileManager().addInput(action, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, fastqFile);

            AnalysisStep step = provider.create(taskHelper);
            AnalysisStep.Output o = step.performAnalysisPerSampleLocal(model, inputBam, refFasta);
            if (o != null)
            {
                ret.add(o);
                taskHelper.getFileManager().addStepOutputs(action, o);
            }

            actions.add(action);
        }

        return ret;
    }

    public static List<AnalysisStep.Output> runAnalysesRemote(List<RecordedAction> actions, Readset rs, File inputBam, ReferenceGenome referenceGenome, List<PipelineStepProvider<AnalysisStep>> providers, SequenceTaskHelper taskHelper) throws PipelineJobException
    {
        List<AnalysisStep.Output> ret = new ArrayList<>();
        for (PipelineStepProvider<AnalysisStep> provider : providers)
        {
            taskHelper.getJob().getLogger().info("Running " + provider.getLabel() + " for BAM: " + inputBam.getPath());

            RecordedAction action = new RecordedAction(provider.getLabel());
            taskHelper.getFileManager().addInput(action, "Input BAM File", inputBam);
            taskHelper.getFileManager().addInput(action, "Reference DB FASTA", referenceGenome.getSourceFastaFile());

            File outDir = new File(taskHelper.getWorkingDirectory(), FileUtil.getBaseName(inputBam));
            if (!outDir.exists())
            {
                outDir.mkdirs();
            }

            AnalysisStep step = provider.create(taskHelper);
            AnalysisStep.Output o = step.performAnalysisPerSampleRemote(rs, inputBam, referenceGenome, outDir);
            if (o != null)
            {
                ret.add(o);
                taskHelper.getFileManager().addStepOutputs(action, o);
            }

            actions.add(action);
        }

        return ret;
    }
}
