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

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.sequenceanalysis.api.pipeline.AnalysisStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceLibraryStep;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.util.FastqUtils;

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
        List<AnalysisModel> analysisModels = new ArrayList<>();

        for (ReadsetModel rs : taskHelper.getSettings().getReadsets())
        {
            String basename = SequenceTaskHelper.getMinimalBaseName(rs.getFileName());
            String basename2 = SequenceTaskHelper.getMinimalBaseName(rs.getFileName2());

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
                    if (basename.equals(SequenceTaskHelper.getMinimalBaseName(d.getFile())))
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
            ReferenceLibraryStep referenceLibraryStep = taskHelper.getSingleStep(ReferenceLibraryStep.class).create(taskHelper);
            referenceLibraryStep.setLibraryId(getJob(), run, model);
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

                    if (basename != null && basename.equals(SequenceTaskHelper.getMinimalBaseName(d.getFile())))
                    {
                        //if the file doesnt exist, such as a deleted intermediate, dont override a potentially better input
                        if (!d.getFile().exists() && model.getInputFile() != null)
                        {
                            getJob().getLogger().debug("A file is already been selected for input1.  match will be skipped: " + d.getFile().getPath());
                        }
                        else
                        {
                            model.setInputFile(d.getRowId());
                        }
                    }

                    if (basename2 != null && basename2.equals(SequenceTaskHelper.getMinimalBaseName(d.getFile())))
                    {
                        //if the file doesnt exist, such as a deleted intermediate, dont override a potentially better input
                        if (!d.getFile().exists() && model.getInputFile2() != null)
                        {
                            getJob().getLogger().debug("A file is already been selected for input2.  match will be skipped: " + d.getFile().getPath());
                        }
                        else
                        {
                            model.setInputFile2(d.getRowId());
                        }
                    }
                }
            }

            if (model.getInputFile() == null)
            {
                getJob().getLogger().error("Unable to find first input FASTQ file for run: " + run.getRowId() + ". Expected file beginning with: " + basename);
                continue;
            }

            if (!StringUtils.isEmpty(rs.getFileName2()) && model.getInputFile2() == null)
            {
                getJob().getLogger().error("Unable to find second input FASTQ file for run: " + run.getRowId() + ". Expected file beginning with: " + basename2);
                continue;
            }

            analysisModels.add(model);
        }

        if (analysisModels.size() == 0)
        {
            getJob().getLogger().info("No analyses were created");
        }

        int i = 0;
        for (AnalysisModel model : analysisModels)
        {
            try
            {
                i++;
                getJob().getLogger().info("processing BAM " + i + " of " + analysisModels.size());

                TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
                Table.insert(getJob().getUser(), ti, model);
                addMetricsForAnalysis(model);

                List<PipelineStepProvider<AnalysisStep>> providers = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
                if (!providers.isEmpty())
                {
                    File bam = ExperimentService.get().getExpData(model.getAlignmentFile()).getFile();
                    if(bam == null)
                    {
                        getJob().getLogger().error("Skipping SNP calling, unable to find BAM");
                        continue;
                    }

                    File refDB = ExperimentService.get().getExpData(model.getReferenceLibrary()).getFile();
                    if(refDB == null)
                    {
                        getJob().getLogger().error("Skipping SNP calling, unable to find reference DB");
                        continue;
                    }

                    runAnalyses(actions, model, bam, refDB, providers, taskHelper);
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        return new RecordedActionSet();
    }

    private void addMetricsForAnalysis(AnalysisModel model) throws SQLException
    {
        int analysisId = model.getRowId();

        if (model.getAlignmentFile() != null)
        {

        }

        if (model.getInputFile() != null)
        {
            addFastqFileMetrics(analysisId, model.getInputFile());
        }

        if (model.getInputFile2() != null)
        {
            addFastqFileMetrics(analysisId, model.getInputFile());
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

        Map<String, Object> metricsMap = FastqUtils.getQualityMetrics(d.getFile());
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

    public static List<AnalysisStep.Output> runAnalyses(List<RecordedAction> actions, AnalysisModel model, File inputBam, File refFasta, List<PipelineStepProvider<AnalysisStep>> providers, SequenceTaskHelper taskHelper) throws PipelineJobException
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
            AnalysisStep.Output o = step.performAnalysisPerSample(model, inputBam, refFasta);
            if (o != null)
            {
                ret.add(o);
            }

            actions.add(action);
        }

        return ret;
    }
}
