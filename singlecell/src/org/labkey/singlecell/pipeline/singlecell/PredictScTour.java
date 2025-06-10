package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PredictScTour extends AbstractRiraStep
{
    public PredictScTour(PipelineContext ctx, PredictScTour.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("PredictScTour", "Run scTour", "scTour", "This will run scTour using the pre-computed model.", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam("modelFileId", "Model", "This is the pre-computed scTour", "sequenceanalysis-sequenceoutputfileselectorfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("category", TrainScTour.CATEGORY);
                        put("performGenomeFilter", false);
                    }}, null)
            ), null, null);
        }

        @Override
        public PredictScTour create(PipelineContext ctx)
        {
            return new PredictScTour(ctx, this);
        }
    }

    @Override
    protected Chunk createParamChunk(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Integer fileId = getProvider().getParameterByName("modelFileId").extractValue(ctx.getJob(), getProvider(), getStepIdx(), Integer.class);
        if (fileId == null)
        {
            throw new PipelineJobException("Missing value for modelFileId param");
        }

        File model = ctx.getSequenceSupport().getCachedData(fileId);

        Chunk ret = super.createParamChunk(ctx, inputObjects, outputPrefix);
        ret.bodyLines.add("modelFile <- '" + model.getPath() + "'");

        return ret;
    }

    @Override
    protected Collection<File> getAdditionalDockerInputs(SequenceOutputHandler.JobContext ctx) throws PipelineJobException
    {
        Integer fileId = getProvider().getParameterByName("modelFileId").extractValue(ctx.getJob(), getProvider(), getStepIdx(), Integer.class);
        if (fileId == null)
        {
            throw new PipelineJobException("Missing value for modelFileId param");
        }

        return Collections.singleton(ctx.getSequenceSupport().getCachedData(fileId));
    }
}
