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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;

import java.io.File;
import java.io.IOException;
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

        @Override
        public WorkDirectory createWorkDirectory(String jobGUID, FileAnalysisJobSupport jobSupport, Logger logger) throws IOException
        {
            WorkDirFactory factory = PipelineJobService.get().getWorkDirFactory();

            return factory.createWorkDirectory(jobGUID, jobSupport, true, logger);
        }
    }

    public SequenceAlignmentJob getPipelineJob()
    {
        return (SequenceAlignmentJob)getJob();
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        SequenceTaskHelper taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);

        List<RecordedAction> actions = new ArrayList<>();

        job.getLogger().info("Processing Alignments");

        //first validate all analysis records before actually creating any DB records
        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());
        ExpRun run = ExperimentService.get().getExpRun(runId);
        AnalysisModelImpl analysisModel = null;

        if (SequenceTaskHelper.isAlignmentUsed(getJob()))
        {
            SequenceReadsetImpl rs = getPipelineJob().getReadset();
            String basename = rs.getLegalFileName();

            analysisModel = new AnalysisModelImpl();
            analysisModel.setContainer(getJob().getContainer().getId());
            analysisModel.setCreatedby(getJob().getUser().getUserId());
            analysisModel.setCreated(new Date());
            analysisModel.setModifiedby(getJob().getUser().getUserId());
            analysisModel.setModified(new Date());
            analysisModel.setDescription(taskHelper.getSettings().getJobDescription());
            analysisModel.setRunId(runId);
            analysisModel.setReadset(rs.getReadsetId());

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

                        analysisModel.setAlignmentFile(d.getRowId());
                        found = true;
                    }
                }
            }

            if (analysisModel.getAlignmentFile() == null)
            {
                getJob().getLogger().info("Total BAMs found : " + datas.size());
                for (ExpData d : datas)
                {
                    getJob().getLogger().info("\t" + d.getFile().getPath());
                }

                throw new PipelineJobException("Unable to find BAM for run: " + run.getRowId() + ". Expected file beginning with: " + basename);
            }

            //reference
            ReferenceGenome referenceGenome = getPipelineJob().getTargetGenome();
            if (referenceGenome == null)
            {
                throw new PipelineJobException("unable to find cached reference genome");
            }

            analysisModel.setLibraryId(referenceGenome.getGenomeId());
            if (referenceGenome.getFastaExpDataId() != null)
            {
                analysisModel.setReferenceLibrary(referenceGenome.getFastaExpDataId());
            }
            else
            {
                List<? extends ExpData> fastaDatas = run.getInputDatas(IndexOutputImpl.REFERENCE_DB_FASTA, null);
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
                            analysisModel.setReferenceLibrary(d.getRowId());
                            break;
                        }
                        else
                        {
                            job.getLogger().debug("File does not exist: " + d.getFile().getPath());
                        }
                    }
                }
            }

            if (analysisModel.getReferenceLibrary() == null)
            {
                throw new PipelineJobException("Unable to find reference FASTA for run: " + run.getRowId());
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
                    }
                }
            }
        }

        //optionally delete the BAM itself:
        boolean discardBam = false;
        if (SequenceTaskHelper.isAlignmentUsed(getJob()))
        {
            AlignmentStep alignmentStep = taskHelper.getSingleStep(AlignmentStep.class).create(taskHelper);
            ToolParameterDescriptor discardBamParam = alignmentStep.getProvider().getParameterByName(AbstractAlignmentStepProvider.DISCARD_BAM);
            discardBam = discardBamParam.extractValue(getJob(), alignmentStep.getProvider(), Boolean.class, false);
        }

        if (analysisModel == null)
        {
            getJob().getLogger().info("No analyses were created");
        }
        else
        {
            processAnalyses(analysisModel, runId, actions, taskHelper, discardBam);
        }


        if (SequenceTaskHelper.isAlignmentUsed(getJob()))
        {
            //build map used next to import metrics
            Map<Integer, Integer> readsetToAnalysisMap = new HashMap<>();
            Map<Integer, Map<PipelineStepOutput.PicardMetricsOutput.TYPE, File>> typeMap = new HashMap<>();
            readsetToAnalysisMap.put(analysisModel.getReadset(), analysisModel.getRowId());
            typeMap.put(analysisModel.getReadset(), new HashMap<>());

            typeMap.get(analysisModel.getReadset()).put(PipelineStepOutput.PicardMetricsOutput.TYPE.bam, analysisModel.getAlignmentFileObject());
            typeMap.get(analysisModel.getReadset()).put(PipelineStepOutput.PicardMetricsOutput.TYPE.reads, analysisModel.getAlignmentFileObject());
            taskHelper.getFileManager().writeMetricsToDb(readsetToAnalysisMap, typeMap);
            taskHelper.getFileManager().createSequenceOutputRecords(analysisModel.getRowId());

            if (discardBam)
            {
                File bam = analysisModel.getAlignmentFileObject();
                if (bam != null)
                {
                    getJob().getLogger().info("BAM will be discarded: " + bam.getName());
                    bam.delete();
                    File idx = new File(bam.getPath() + ".bai");
                    if (idx.exists())
                    {
                        idx.delete();
                    }

                    analysisModel.setAlignmentFile(null);
                    getJob().getLogger().info("creating analysis record for BAM: " + bam.getName());
                    TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ANALYSES);

                    Table.update(getJob().getUser(), ti, analysisModel, analysisModel.getRowId());
                }
            }
        }

        return new RecordedActionSet();
    }

    private void processAnalyses(AnalysisModelImpl analysisModel, int runId, List<RecordedAction> actions, SequenceTaskHelper taskHelper, boolean discardBam) throws PipelineJobException
    {
        List<AnalysisStep.Output> outputs = new ArrayList<>();
        List<PipelineStepProvider<AnalysisStep>> providers = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
        if (providers.isEmpty())
        {
            getJob().getLogger().info("no analyses were selected");
        }

        getJob().getLogger().info("processing analysis: " + analysisModel.getRowId());

        File bam = ExperimentService.get().getExpData(analysisModel.getAlignmentFile()).getFile();
        if (bam == null)
        {
            getJob().getLogger().error("unable to find BAM, skipping");
            return;
        }

        File refDB = ExperimentService.get().getExpData(analysisModel.getReferenceLibrary()).getFile();
        if (refDB == null)
        {
            getJob().getLogger().error("unable to find reference fasta, skipping");
            return;
        }

        getJob().getLogger().info("creating analysis record for BAM: " + bam.getName());
        TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
        Table.insert(getJob().getUser(), ti, analysisModel);

        if (!discardBam)
        {
            SequenceOutputFile so = new SequenceOutputFile();
            so.setName(bam.getName());
            so.setCategory("Alignment");
            so.setAnalysis_id(analysisModel.getAnalysisId());
            so.setReadset(analysisModel.getReadset());
            so.setLibrary_id(analysisModel.getLibrary_Id());
            AlignmentStep alignmentStep = taskHelper.getSingleStep(AlignmentStep.class).create(taskHelper);
            so.setDescription("Aligner: " + alignmentStep.getProvider().getName());
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
            so.setRunId(runId);

            Table.insert(getJob().getUser(), SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), so);
        }
        else
        {
            getJob().getLogger().debug("BAM will be discarded, will not create output file");
        }

        if (!providers.isEmpty())
        {
            outputs.addAll(runAnalysesLocal(actions, analysisModel, bam, refDB, providers, taskHelper, bam.getParentFile()));
        }
    }

    public static List<AnalysisStep.Output> runAnalysesLocal(List<RecordedAction> actions, AnalysisModel model, File inputBam, File refFasta, List<PipelineStepProvider<AnalysisStep>> providers, SequenceTaskHelper taskHelper, File outDir) throws PipelineJobException
    {
        List<AnalysisStep.Output> ret = new ArrayList<>();
        for (PipelineStepProvider<AnalysisStep> provider : providers)
        {
            taskHelper.getJob().getLogger().info("Running " + provider.getLabel() + " for analysis: " + model.getRowId());
            taskHelper.getJob().setStatus(PipelineJob.TaskStatus.running, ("Running: " + provider.getLabel()).toUpperCase());
            taskHelper.getJob().getLogger().info("\tUsing alignment: " + inputBam.getPath());

            RecordedAction action = new RecordedAction(provider.getLabel());
            taskHelper.getFileManager().addInput(action, "Input BAM File", inputBam);
            taskHelper.getFileManager().addInput(action, "Reference DB FASTA", refFasta);
            //taskHelper.getFileManager().addInput(action, SequenceTaskHelper.FASTQ_DATA_INPUT_NAME, fastqFile);

            AnalysisStep step = provider.create(taskHelper);
            AnalysisStep.Output o = step.performAnalysisPerSampleLocal(model, inputBam, refFasta, outDir);
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
            taskHelper.getJob().setStatus(PipelineJob.TaskStatus.running, ("Running: " + provider.getLabel()).toUpperCase());

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
