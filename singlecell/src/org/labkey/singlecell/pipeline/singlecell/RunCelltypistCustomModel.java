package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
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
                    SeuratToolParameter.create("columnPrefix", "Column Prefix", "This string will be pre-pended to the normal output columns (i.e. majority_voting and predicted_labels)", "textfield", null, null),
                    SeuratToolParameter.create("maxAllowableClasses", "Max Allowable Classes", "Celltypist can assign a cell to many classes, creating extremely long labels. Any cell with more than this number of labels will be set to NA", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 5),
                    SeuratToolParameter.create("minFractionToInclude", "Min Fraction To Include", "Any labels representing fewer than this fraction of the cells will be set to NA", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 3);
                    }}, 0.01),
                    SeuratToolParameter.create( "mode", "Mode", "The build-in model(s) to use.", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "best_match;prob_match");
                        put("allowBlank", false);
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, "prob_match", null, true, true).delimiter(";"),
                    SeuratToolParameter.create("useMajorityVoting", "Majority Voting", "If true, celltypist will be run using --majority-voting", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("retainProbabilityMatrix", "Retain Probability Matrix", "If true, the celltypist probability_matrix with per-class probabilities will be stored in meta.data", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("pThreshold", "pThreshold", "This is passed to the --p-thres argument.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 3);
                    }}, 0.5),
                    SeuratToolParameter.create("minProp", "minProp", "This is passed to the --min-prop argument.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 3);
                    }}, 0),
                    SeuratToolParameter.create("maxBatchSize", "Max Cells Per Batch", "If the object has more than this many cells, celltypist will be run in batches", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 500000)
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
