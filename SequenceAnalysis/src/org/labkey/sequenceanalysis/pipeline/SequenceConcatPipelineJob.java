package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.security.User;

import java.io.IOException;
import java.util.List;

/**
 * Created by bimber on 11/3/2016.
 */
public class SequenceConcatPipelineJob extends SequenceJob
{
    public static final String FOLDER_NAME = "sequenceImport";

    // Default constructor for serialization
    protected SequenceConcatPipelineJob()
    {
    }

    public SequenceConcatPipelineJob(Container c, User user, PipeRoot pipeRoot, String sequenceName, String sequenceDescription, @Nullable List<Integer> sequenceIds) throws IOException
    {
        super(SequencePipelineProvider.NAME, c, user, "Concatenate Sequences", pipeRoot, new JSONObject(){{
            put("sequenceIds", sequenceIds);
            put("sequenceName", sequenceName);
            put("sequenceDescription", sequenceDescription);
        }}, new TaskId(SequenceConcatPipelineJob.class), FOLDER_NAME);
    }
}
