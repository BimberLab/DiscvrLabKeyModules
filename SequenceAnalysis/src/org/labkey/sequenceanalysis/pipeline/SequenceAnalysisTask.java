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
import org.labkey.sequenceanalysis.analysis.AASnpByCodonAggregator;
import org.labkey.sequenceanalysis.analysis.AlignmentAggregator;
import org.labkey.sequenceanalysis.analysis.AvgBaseQualityAggregator;
import org.labkey.sequenceanalysis.analysis.BamIterator;
import org.labkey.sequenceanalysis.analysis.MetricsAggregator;
import org.labkey.sequenceanalysis.analysis.NtCoverageAggregator;
import org.labkey.sequenceanalysis.analysis.NtSnpByPosAggregator;
import org.labkey.sequenceanalysis.analysis.SequenceBasedTypingAlignmentAggregator;
import org.labkey.sequenceanalysis.model.AnalysisModel;
import org.labkey.sequenceanalysis.model.ReadsetModel;
import org.labkey.sequenceanalysis.util.FastqUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/23/12
 * Time: 9:40 AM
 */
public class SequenceAnalysisTask extends WorkDirectoryTask<SequenceAnalysisTask.Factory>
{
    private SequenceTaskHelper _taskHelper;
    private static String ACTION_NAME = "Run Analyses";
    private static String RUNNING_ANALYSIS_STATUS = "Running Analyses";

    private HashMap<String, List<Object[]>> _outputFiles = new HashMap<>();
    private HashMap<String, List<Object[]>> _inputFiles = new HashMap<>();

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
            return Arrays.asList(ACTION_NAME);
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
        _taskHelper = new SequenceTaskHelper(job);

        List<RecordedAction> actions = new ArrayList<>();

        job.getLogger().info("Processing Alignments");

        //first validate all analysis records before actually creating any DB records
        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());
        ExpRun run = ExperimentService.get().getExpRun(runId);
        List<AnalysisModel> analysisModels = new ArrayList<>();

        for (ReadsetModel rs : _taskHelper.getSettings().getReadsets())
        {
            String basename = SequenceTaskHelper.getMinimalBaseName(rs.getFileName());
            String basename2 = SequenceTaskHelper.getMinimalBaseName(rs.getFileName2());

            AnalysisModel model = new AnalysisModel();
            model.setContainer(getJob().getContainer().getId());
            model.setCreatedby(getJob().getUser().getUserId());
            model.setCreated(new Date());
            model.setModifiedby(getJob().getUser().getUserId());
            model.setModified(new Date());
            model.setType(_taskHelper.getSettings().getAnalysisType());
            model.setDescription(_taskHelper.getSettings().getAnalysisDescription());
            model.setRunId(runId);
            model.setReadset(rs.getReadsetId());

            //find BAM
            ExpData[] datas = run.getInputDatas(SequenceAlignmentTask.BAM_ROLE, ExpProtocol.ApplicationType.ExperimentRunOutput);
            if (datas.length > 0)
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
                getJob().getLogger().info("Total BAMs found : " + datas.length);
                for (ExpData d : datas)
                {
                    getJob().getLogger().info("\t" + d.getFile().getPath());
                }
                continue;
            }

            //reference library
            datas = run.getInputDatas(ReferenceLibraryTask.REFERENCE_DB_FASTA, null);
            if (datas.length > 0)
            {
                for (ExpData d : datas)
                {
                    if (d.getFile() == null)
                    {
                        getJob().getLogger().debug("No file found for ExpData: " + d.getRowId());
                    }
                    else if (d.getFile().exists())
                    {
                        model.setReference_library(d.getRowId());
                        break;
                    }
                    else
                    {
                        getJob().getLogger().debug("File does not exist: " + d.getFile().getPath());
                    }
                }
            }

            if (model.getReference_library() == null)
            {
                getJob().getLogger().error("Unable to find reference FASTA for run: " + run.getRowId());
                continue;
            }

            //input FASTQs
            datas = run.getInputDatas(SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, ExpProtocol.ApplicationType.ProtocolApplication);
            if (datas.length > 0)
            {
                for (ExpData d : datas)
                {
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

                if (_taskHelper.getSettings().hasAdditionalAnalyses())
                {
                    File bam = ExperimentService.get().getExpData(model.getAlignmentFile()).getFile();
                    if(bam == null)
                    {
                        getJob().getLogger().error("Skipping SNP calling, unable to find BAM");
                        continue;
                    }

                    File refDB = ExperimentService.get().getExpData(model.getReference_library()).getFile();
                    if(refDB == null)
                    {
                        getJob().getLogger().error("Skipping SNP calling, unable to find reference DB");
                        continue;
                    }

                    File inputFile = ExperimentService.get().getExpData(model.getInputFile()).getFile();
                    if(inputFile == null)
                    {
                        getJob().getLogger().error("Skipping SNP calling, unable to find input FASTQ");
                        continue;
                    }

                    if (_taskHelper.getSettings().hasCustomReference())
                    {
                        getJob().getLogger().info("This run used a custom reference, cannot run downstream analyses");
                        continue;
                    }

                    RecordedAction analysesAction = runAnalyses(model, bam, refDB, inputFile);
                    if (analysesAction != null)
                        actions.add(analysesAction);
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

    private RecordedAction runAnalyses(AnalysisModel model, File inputFile, File refFile, File fastqFile) throws PipelineJobException
    {
        PipelineJob job = getJob();

        RecordedAction action = new RecordedAction(ACTION_NAME);
        addInput(action, "Input BAM File", inputFile);
        addInput(action, "Reference DB FASTA", refFile);
        addInput(action, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, fastqFile);

        getJob().getLogger().info("Running analyses for analysis: " + model.getRowId());
        job.getLogger().info("\tUsing alignment: " + inputFile.getPath());

        try
        {
            //first calculate avg qualities at each position
            getJob().getLogger().info("Calculating avg quality scores");
            AvgBaseQualityAggregator avg = new AvgBaseQualityAggregator(inputFile, refFile, getJob().getLogger());
            avg.calculateAvgQuals();
            getJob().getLogger().info("\tCalculation complete");

            getJob().getLogger().info("Inspecting alignments in BAM");
            BamIterator bi = new BamIterator(inputFile, refFile, getJob().getLogger());

            List<AlignmentAggregator> aggregators = new ArrayList<>();
            for (String name : _taskHelper.getSettings().getAggregatorNames())
            {
                if (name.equals(SequencePipelineSettings.NT_SNP_AGGREGATOR))
                    aggregators.add(new NtSnpByPosAggregator(_taskHelper.getSettings(), getJob().getLogger(), avg));
                else if (name.equals(SequencePipelineSettings.COVERAGE_AGGREGATOR))
                    aggregators.add(new NtCoverageAggregator(_taskHelper.getSettings(), getJob().getLogger(), avg));
                else if (name.equals(SequencePipelineSettings.AA_SNP_BY_CODON_AGGREGATOR))
                    aggregators.add(new AASnpByCodonAggregator(_taskHelper.getSettings(), getJob().getLogger(), avg));
                else if (name.equals(SequencePipelineSettings.SBT_AGGREGATOR))
                    aggregators.add(new SequenceBasedTypingAlignmentAggregator(_taskHelper.getSettings(), getJob().getLogger(), avg));
            }

            aggregators.add(new MetricsAggregator(inputFile, getJob().getLogger()));

            bi.addAggregators(aggregators);
            bi.iterateReads();

            getJob().getLogger().info("Inspection complete");

            for (AlignmentAggregator a : aggregators)
            {
                a.saveToDb(getJob().getUser(), getJob().getContainer(), model);
            }

            bi.saveSynopsis(getJob().getUser(), model);

            return action;
        }
        catch (FileNotFoundException e)
        {
            throw new PipelineJobException(e.getMessage());
        }
    }

    private void addInput(RecordedAction action, String role, File file)
    {
        _taskHelper.addInputOutput(action, role, file, _inputFiles, "Input");
    }
}
