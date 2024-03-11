package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class TrainScTour extends AbstractRiraStep
{
    public static final String CATEGORY = "scTour Classifier";

    public TrainScTour(PipelineContext ctx, TrainScTour.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("TrainScTour", "Train scTour", "scTour", "This will run scTour on the input seurat object to infer pseudotime.", Arrays.asList(
                    SeuratToolParameter.create("modelName", "Model Name", "The name of the resulting model", "textfield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.create("modelDescription", "Description", "Used to described the resulting model", "textarea", new JSONObject()
                    {{
                        put("width", 400);
                        put("height", 200);
                    }}, null),
                    SeuratToolParameter.create("featureExclusionList", "Features to Exclude", "These genes, entered comma-separated or one/line, will be excluded from the input object", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/(^['\"]+)|(['\"]+$)/g");
                    }}, "VariableGenes_Exclusion.2").delimiter(",")
            ), null, null);
        }

        @Override
        public TrainScTour create(PipelineContext ctx)
        {
            return new TrainScTour(ctx, this);
        }
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return true;
    }

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        String modelName = StringUtils.trimToNull(getProvider().getParameterByName("modelName").extractValue(ctx.getJob(), getProvider(), getStepIdx(), String.class, null));
        if (modelName == null)
        {
            throw new PipelineJobException("Missing modelName param");
        }

        Output output = super.execute(ctx, inputObjects, outputPrefix);
        File modelFile = new File(ctx.getOutputDir(), "scTourModel.pth");
        if (!modelFile.exists())
        {
            throw new PipelineJobException("Unable to find file: " + modelFile.getPath());
        }

        SequenceOutputFile so = new SequenceOutputFile();
        so.setFile(modelFile);
        so.setCategory(CATEGORY);
        so.setLibrary_id(ctx.getSequenceSupport().getCachedGenomes().iterator().next().getGenomeId());
        so.setName(modelName);
        so.setDescription(getProvider().getParameterByName("modelDescription").extractValue(ctx.getJob(), getProvider(), getStepIdx(), String.class, null));

        ctx.getFileManager().addSequenceOutput(so);

        return output;
    }
}