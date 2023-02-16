package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceOutputHandlerFinalTask extends PipelineJob.Task<SequenceOutputHandlerFinalTask.Factory>
{
    private static final String ACTION_NAME = "Processing Files";

    protected SequenceOutputHandlerFinalTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(SequenceOutputHandlerFinalTask.class);
            setLocation("webserver");
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(ACTION_NAME);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SequenceOutputHandlerFinalTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private SequenceOutputHandlerJob getPipelineJob()
    {
        return (SequenceOutputHandlerJob)getJob();
    }

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());
        getPipelineJob().setExperimentRunRowId(runId);

        //create analysisRecord
        AnalysisModelImpl am = new AnalysisModelImpl();
        am.setContainer(getJob().getContainerId());
        String description = getPipelineJob().getParameters().getOrDefault("jobDescription", null) != null ? getPipelineJob().getParameters().get("jobDescription") : getPipelineJob().getDescription();
        am.setDescription(description);
        am.setRunId(runId);


        Set<Integer> genomeIds = new HashSet<>();
        for (SequenceOutputFile o : getPipelineJob().getFiles())
        {
            if (o.getLibrary_id() != null)
            {
                genomeIds.add(o.getLibrary_id());
            }
        }

        if (genomeIds.size() == 1)
        {
            am.setLibraryId(genomeIds.iterator().next());
        }

        am.setCreated(new Date());
        am.setModified(new Date());
        am.setCreatedby(getJob().getUser().getUserId());
        am.setModifiedby(getJob().getUser().getUserId());
        am.setType(getPipelineJob().getHandler().getAnalysisType(getJob()));
        TableInfo analysisTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_ANALYSES);

        Set<Integer> readsetIds = new HashSet<>();
        for (SequenceOutputFile so : getPipelineJob().getFiles())
        {
            if (so.getReadset() == null)
            {
                readsetIds.clear();
                break;
            }

            readsetIds.add(so.getReadset());
        }

        if (readsetIds.size() == 1)
        {
            am.setReadset(readsetIds.iterator().next());
        }

        Table.insert(getJob().getUser(), analysisTable, am);
        int analysisId = am.getRowId();

        List<SequenceOutputFile> outputsCreated = new ArrayList<>();
        if (!getPipelineJob().getOutputsToCreate().isEmpty())
        {
            outputsCreated.addAll(createOutputFiles(getPipelineJob(), runId, analysisId));
        }
        else
        {
            getJob().getLogger().info("no outputs created, nothing to do");
        }

        //run final handler
        getPipelineJob().getHandler().getProcessor().complete(getPipelineJob(), getPipelineJob().getFiles(), outputsCreated, getPipelineJob().getSequenceSupport());

        File xml = getPipelineJob().getSerializedOutputFilesFile();
        if (xml.exists())
        {
            getJob().getLogger().debug("deleting outputfiles XML file: " + xml.getPath());
            xml.delete();
        }

        return new RecordedActionSet();
    }

    public static Set<SequenceOutputFile> createOutputFiles(SequenceJob job, int runId, @Nullable Integer analysisId)
    {
        job.getLogger().info("creating " + job.getOutputsToCreate().size() + " new output files for run: " + runId);

        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
        Set<SequenceOutputFile> created = new HashSet<>();

        for (SequenceOutputFile o : job.getOutputsToCreate())
        {
            updateOutputFile(o, job, runId, analysisId);

            // this is a check added to debug the intermittent situation where outputs are double-created.  I think it happens
            // in a rare case where a job was waiting to run during a server restart, and the task is double-started.
            if (o.getRunId() != null)
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("runId"), runId, CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("dataId"), o.getDataId(), CompareType.EQUAL);
                if (o.getCategory() != null)
                {
                    filter.addCondition(FieldKey.fromString("category"), o.getCategory(), CompareType.EQUAL);
                }
                
                filter.addCondition(FieldKey.fromString("name"), o.getName(), CompareType.EQUAL);
                TableSelector ts = new TableSelector(ti, filter, null);
                if (ts.exists())
                {
                    job.getLogger().warn("Existing output file found, skipping: " + o.getName() + ", dataid: " + o.getDataId());
                    created.add(ts.getObject(SequenceOutputFile.class));
                    continue;
                }
            }

            created.add(Table.insert(job.getUser(), ti, o));
        }

        job.getLogger().debug("clearing cached job outputs");
        job.getOutputsToCreate().clear();

        return created;
    }

    public static void updateOutputFile(SequenceOutputFile o, PipelineJob job, Integer runId, Integer analysisId)
    {
        o.setRunId(runId);
        o.setAnalysis_id(analysisId);
        o.setCreatedby(job.getUser().getUserId());
        if (o.getCreated() == null)
        {
            o.setCreated(new Date());
        }

        o.setModifiedby(job.getUser().getUserId());
        if (o.getModified() == null)
        {
            o.setModified(new Date());
        }

        if (o.getContainer() == null)
        {
            o.setContainer(job.getContainerId());
        }

        if (o.getDataId() == null && o.getFile() != null)
        {
            job.getLogger().debug("possibly creating ExpData for file: " + o.getFile().getName());
            ExpData d = ExperimentService.get().getExpDataByURL(o.getFile(), job.getContainer());
            if (d != null)
            {
                job.getLogger().debug("Existing ExpData found, using: " + d.getRowId() + ", " + d.getFilePath().toString());
            }
            else
            {
                d = ExperimentService.get().createData(job.getContainer(), new DataType(o.getCategory()));
                d.setDataFileURI(o.getFile().toURI());
                d.setName(o.getFile().getName());
                d.save(job.getUser());
            }

            o.setDataId(d.getRowId());
        }
    }
}
