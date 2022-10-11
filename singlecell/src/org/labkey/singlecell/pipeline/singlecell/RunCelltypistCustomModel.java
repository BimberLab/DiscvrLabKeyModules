package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.io.FileUtils;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class RunCelltypistCustomModel extends AbstractRiraStep
{
    public RunCelltypistCustomModel(PipelineContext ctx, RunCelltypistCustomModel.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunCelltypistCustomModel", "Run Celltypist (Custom Model)", "Celltypist", "This will run celltypist using the selected model.", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam("modelFileId", "Model", "This is the pre-computed celltypist model to use for classification", "sequenceanalysis-sequenceoutputfileselectorfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("category", TrainCelltypist.CATEGORY);
                        put("performGenomeFilter", false);
                    }}, null),
                    ToolParameterDescriptor.create("columnPrefix", "Column Prefix", "This string will be pre-pended to the normal output columns (i.e. majority_voting and predicted_labels)", "textfield", null, null)
            ), PageFlowUtil.set("sequenceanalysis/field/SequenceOutputFileSelectorField.js"), null);
        }

        @Override
        public RunCelltypistCustomModel create(PipelineContext ctx)
        {
            return new RunCelltypistCustomModel(ctx, this);
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
        File local = new File(ctx.getWorkingDirectory(), model.getName());
        if (local.exists())
        {
            local.delete();
        }

        try
        {
            FileUtils.copyFile(model, local);
            ctx.getFileManager().addIntermediateFile(local);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        Chunk ret = super.createParamChunk(ctx, inputObjects, outputPrefix);
        ret.bodyLines.add("modelFile <- '/work/" + local.getName() + "'");

        return ret;
    }
}
