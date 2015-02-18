package org.labkey.sequenceanalysis.pipeline;

import org.json.JSONObject;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
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
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.api.sequenceanalysis.model.ReadsetModel;

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

        public String getStatusName()
        {
            return "Importing Alignment";
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList();
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            AlignmentImportTask task = new AlignmentImportTask(this, job);
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
        //create analysis records
        parseAndCreateAnalyses();

        return new RecordedActionSet();
    }

    private List<AnalysisModel> parseAndCreateAnalyses() throws PipelineJobException
    {
        SequenceTaskHelper taskHelper = new SequenceTaskHelper(getJob(), _wd);

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
            TableInfo rs = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READSETS);
            TableInfo analyses = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
            TableInfo outputFiles = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);

            List<AnalysisModel> ret = new ArrayList<>();
            Map<String, String> params = getJob().getParameters();
            for (String key : params.keySet())
            {
                if (key.startsWith("sample_"))
                {
                    JSONObject o = new JSONObject(params.get(key));

                    ReadsetModel r = settings.createReadsetModel(o);
                    if (r.getReadsetId() == null || r.getReadsetId() == 0)
                    {
                        r.setFileId(null);
                        r.setFileId2(null);
                        r.setContainer(getJob().getContainer().getId());
                        r.setCreated(new Date());
                        r.setCreatedBy(getJob().getUser().getUserId());
                        r.setModified(new Date());
                        r.setModifiedBy(getJob().getUser().getUserId());
                        r.setRunId(runId);

                        r = Table.insert(getJob().getUser(), rs, r);
                        getJob().getLogger().info("Created readset: " + r.getRowId());
                    }
                    else
                    {
                        //verify this readset exists:
                        r = new TableSelector(rs, new SimpleFilter(FieldKey.fromString("rowid"), r.getRowId()), null).getObject(ReadsetModel.class);
                        if (r == null)
                        {
                            throw new PipelineJobException("Readset with RowId: " + r.getRowId() + " does not exist, aborting");
                        }
                    }

                    AnalysisModelImpl a = new AnalysisModelImpl();
                    a.setReadset(r.getRowId());

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
                    outputFile.setCategory("BAM");
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

            return ret;
        }
    }
}
