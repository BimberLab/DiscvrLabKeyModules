package org.labkey.sequenceanalysis.pipeline;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

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
 * Created by bimber on 11/3/2016.
 */
public class SequenceConcatTask extends PipelineJob.Task<SequenceConcatTask.Factory>
{
    protected SequenceConcatTask(SequenceConcatTask.Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, SequenceConcatTask.Factory>
    {
        public Factory()
        {
            super(SequenceConcatTask.class);
            //setLocation("webserver");
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
            return List.of("Concatenate Sequences");
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SequenceConcatTask(this, job);
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
        List<RecordedAction> actions = new ArrayList<>();

        List<Integer> sequenceIds = new ArrayList<>();
        for (Object o : getPipelineJob().getParameterJson().getJSONArray("sequenceIds").toList())
        {
            sequenceIds.add((Integer)o);
        }

        try
        {
            createConcatenatedSequence(sequenceIds, getPipelineJob().getParameterJson().getString("sequenceName"), getPipelineJob().getParameterJson().getString("sequenceDescription"), getJob().getContainer(), getJob().getUser(), getPipelineJob().getAnalysisDirectory());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return new RecordedActionSet(actions);
    }

    //100 Ns as a spacer
    private final String Ns = "nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn";

    private int createConcatenatedSequence(List<Integer> sequenceIds, String name, String description, Container c, User u, File outDir) throws IOException
    {
        getJob().getLogger().info("building sequence");
        final StringBuilder sb = new StringBuilder();
        List<RefNtSequenceModel> seqs = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), new SimpleFilter(FieldKey.fromString("rowid"), sequenceIds, CompareType.IN), null).getArrayList(RefNtSequenceModel.class);
        int offset = 0;
        File offsetFile = new File(outDir, "offsetTmp.txt");
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(offsetFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            int i = 0;
            for (RefNtSequenceModel nt : seqs)
            {
                i++;

                if (i % 1000 == 0)
                {
                    getJob().getLogger().info("processed " + i + " sequences");
                }

                writer.writeNext(new String[]{String.valueOf(nt.getRowid()), String.valueOf(offset), nt.getName(), (nt.getGenbank() == null ? "" : nt.getGenbank()), (nt.getRefSeqId() == null ? "" : nt.getRefSeqId())});

                String seq = nt.getSequence();
                if (seq == null)
                {
                    throw new IllegalArgumentException("Sequence not found for: " + nt.getRowid() + ", " + nt.getName());
                }

                offset += seq.length();
                sb.append(seq);
                sb.append(Ns);
                offset += Ns.length();

                nt.clearCachedSequence();
            }
        }

        RefNtSequenceModel m = new RefNtSequenceModel();
        m.setName(name);
        StringBuilder d = new StringBuilder();
        if (description != null)
        {
            d.append(description);
        }
        if (d.length() > 0)
        {
            d.append(", ");
        }
        d.append("total sequences: " + seqs.size());

        m.setComments(d.toString());
        m.setContainer(c.getId());
        m.setCreated(new Date());
        m.setCreatedby(u.getUserId());
        m.setModified(new Date());
        m.setModifiedby(u.getUserId());
        PipelineStatusFile sf = PipelineService.get().getStatusFile(getJob().getLogFile());
        if (sf != null)
        {
            m.setJobId(sf.getRowId());
        }

        m = Table.insert(u, SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), m);

        getJob().getLogger().info("writing sequence file");
        m.createFileForSequence(u, sb.toString(), outDir);

        FileUtils.moveFile(offsetFile, m.getOffsetsFile());

        return m.getRowid();
    }

    private SequenceConcatPipelineJob getPipelineJob()
    {
        return (SequenceConcatPipelineJob)getJob();
    }
}
