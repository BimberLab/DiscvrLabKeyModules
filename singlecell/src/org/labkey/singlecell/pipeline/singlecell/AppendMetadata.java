package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class AppendMetadata extends AbstractRDiscvrStep
{
    public AppendMetadata(PipelineContext ctx, AppendMetadata.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("AppendMetadata", "Append Metadata", "RDiscvr", "This uses Rdiscvr::QueryAndApplyCdnaMetadata to append sample metadata.", Arrays.asList(

            ), null, null);
        }


        @Override
        public AppendMetadata create(PipelineContext ctx)
        {
            return new AppendMetadata(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "metadata";
    }

    @Override
    protected Chunk createParamChunk(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Chunk ret = super.createParamChunk(ctx, inputObjects, outputPrefix);

        ret.bodyLines.add("serverBaseUrl <- '" + getPipelineCtx().getJob().getParameters().get("serverBaseUrl") + "'");
        ret.bodyLines.add("defaultLabKeyFolder <- '" + getPipelineCtx().getJob().getParameters().get("labkeyFolderPath") + "'");

        return ret;
    }
}

