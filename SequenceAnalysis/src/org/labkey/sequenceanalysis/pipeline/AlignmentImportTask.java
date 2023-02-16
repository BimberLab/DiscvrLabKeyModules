package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractSequenceTaskFactory;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/4/2014.
 */
public class AlignmentImportTask extends WorkDirectoryTask<AlignmentImportTask.Factory>
{
    protected AlignmentImportTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(AlignmentImportTask.class);
        }

        @Override
        public String getStatusName()
        {
            return "IMPORTING ALIGNMENT";
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return List.of();
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            AlignmentImportTask task = new AlignmentImportTask(this, job);
            return task;
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new FileType(".bam"));
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        //create analysis records
        parseAndCreateAnalyses();

        return new RecordedActionSet();
    }

    private AlignmentImportJob getPipelineJob()
    {
        return (AlignmentImportJob)getJob();
    }

    private List<AnalysisModel> parseAndCreateAnalyses() throws PipelineJobException
    {
        SequenceTaskHelper taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);

        //find moved BAM files and build map
        Map<String, ExpData> bamMap = new HashMap<>();
        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());
        ExpRun run = ExperimentService.get().getExpRun(runId);
        List<? extends ExpData> datas = run.getInputDatas(SequenceAlignmentTask.FINAL_BAM_ROLE, ExpProtocol.ApplicationType.ExperimentRunOutput);
        if (datas.size() > 0)
        {
            for (ExpData d : datas)
            {
                bamMap.put(d.getFile().getName(), d);
            }
        }

        DbSchema schema = SequenceAnalysisSchema.getInstance().getSchema();
        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
        {

            SequencePipelineSettings settings = new SequencePipelineSettings(getJob().getParameters());

            //ensure readsets exist
            TableInfo analyses = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
            TableInfo outputFiles = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);

            List<AnalysisModel> ret = new ArrayList<>();
            List<Readset> readsets = getPipelineJob().getSequenceSupport().getCachedReadsets();

            Map<String, String> params = getJob().getParameters();
            int idx = 0;
            for (String key : params.keySet())
            {
                if (key.startsWith("readset_"))
                {
                    Readset r = readsets.get(idx);
                    idx++;

                    JSONObject o = new JSONObject(params.get(key));

                    AnalysisModelImpl a = new AnalysisModelImpl();
                    a.setReadset(r.getReadsetId());

                    ExpData movedDataId = bamMap.get(o.getString("fileName"));
                    if (movedDataId == null)
                    {
                        throw new PipelineJobException("Unable to find moved alignment file with name: " + o.getString("fileName"));
                    }

                    a.setAlignmentFile(movedDataId.getRowId());
                    a.setLibrary_id(o.getInt("library_id"));

                    TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
                    Integer refFastaId = new TableSelector(ti, PageFlowUtil.set("fasta_file")).getObject(o.getInt("library_id"), Integer.class);
                    a.setReferenceLibrary(refFastaId);
                    a.setContainer(getJob().getContainer().getId());
                    a.setCreated(new Date());
                    a.setCreatedby(getJob().getUser().getUserId());
                    a.setModified(new Date());
                    a.setModifiedby(getJob().getUser().getUserId());
                    a.setRunId(runId);

                    a = Table.insert(getJob().getUser(), analyses, a);
                    getJob().getLogger().info("Created analysis: " + a.getRowId());
                    ret.add(a);

                    SequenceOutputFile outputFile = new SequenceOutputFile();
                    outputFile.setName(o.getString("fileName"));
                    outputFile.setCategory("Alignment");
                    outputFile.setDataId(movedDataId.getRowId());
                    outputFile.setLibrary_id(o.getInt("library_id"));
                    outputFile.setAnalysis_id(a.getRowId());
                    outputFile.setReadset(a.getReadset());
                    outputFile.setContainer(getJob().getContainer().getId());
                    outputFile.setCreated(new Date());
                    outputFile.setCreatedby(getJob().getUser().getUserId());
                    outputFile.setModified(new Date());
                    outputFile.setModifiedby(getJob().getUser().getUserId());
                    outputFile.setRunId(runId);
                    outputFile = Table.insert(getJob().getUser(), outputFiles, outputFile);
                    getJob().getLogger().info("Created output file: " + outputFile.getRowid());
                }
            }

            transaction.commit();

            //process metrics
            Map<Integer, Integer> readsetToAnalysisMap = new HashMap<>();
            Map<Integer, Map<PipelineStepOutput.PicardMetricsOutput.TYPE, File>> typeMap = new HashMap<>();
            for (AnalysisModel model : ret)
            {
                readsetToAnalysisMap.put(model.getReadset(), model.getRowId());
                typeMap.put(model.getReadset(), new HashMap<>());

                typeMap.get(model.getReadset()).put(PipelineStepOutput.PicardMetricsOutput.TYPE.bam, model.getAlignmentFileObject());
                typeMap.get(model.getReadset()).put(PipelineStepOutput.PicardMetricsOutput.TYPE.reads, model.getAlignmentFileObject());
            }
            taskHelper.getFileManager().writeMetricsToDb(readsetToAnalysisMap, typeMap);

            return ret;
        }
    }
}
