package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.lang3.StringUtils;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class TrainCelltypist extends AbstractRiraStep
{
    public static final String CATEGORY = "Celltypist Classifier";

    public TrainCelltypist(PipelineContext ctx, TrainCelltypist.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("TrainCelltypist", "Train Celltypist", "Celltypist", "This will run Celltypist on the input seurat object to train a classifier.", Arrays.asList(
                    SeuratToolParameter.create("modelName", "Model Name", "The filename of the resulting model", "textfield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.create("modelDescription", "Description", "Used to described the resulting model", "textarea", new JSONObject()
                    {{
                        put("allowBlank", false);
                        put("width", 400);
                        put("height", 200);
                    }}, null),
                    SeuratToolParameter.create("labelField", "Label Field", "The field in the Seurat object to use for labels", "textfield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, null),
                    SeuratToolParameter.create("minCellsPerClass", "Min Cells Per Class", "Any classes with fewer than this many cells will be dropped", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 100),
                    SeuratToolParameter.create("excludedClasses", "Excluded Classes", "Any cells with these labels will be dropped. Note: NA can be used to drop NA values as well.", "textarea", new JSONObject()
                    {{
                        put("width", 400);
                        put("height", 200);
                        put("delimiter", ";");
                    }}, "NA", "excludedClasses", true, true)
            ), null, null);
        }

        @Override
        public TrainCelltypist create(PipelineContext ctx)
        {
            return new TrainCelltypist(ctx, this);
        }
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return false;
    }

    //@Override
    //public void init(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    //{
    //    //NOTE: a valid use-case is to merge/filter many inputs, produce one object, and train, so dont perform this check.
    //    if (inputFiles.size() > 1)
    //    {
    //        throw new PipelineJobException("Celltypist train step expects this job to have a single input. Consider selecting the option to run jobs individually instead of merged");
    //    }
    //}

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Output output = super.execute(ctx, inputObjects, outputPrefix);
        File modelFile = new File(ctx.getOutputDir(), getModelName(ctx.getJob(), true));
        if (!modelFile.exists())
        {
            throw new PipelineJobException("Unable to find file: " + modelFile.getPath());
        }

        SequenceOutputFile so = new SequenceOutputFile();
        so.setFile(modelFile);
        so.setCategory(CATEGORY);
        so.setLibrary_id(ctx.getSequenceSupport().getCachedGenomes().iterator().next().getGenomeId());
        so.setName(getModelName(ctx.getJob(), false));
        so.setDescription(getProvider().getParameterByName("modelDescription").extractValue(ctx.getJob(), getProvider(), getStepIdx(), String.class, null));

        ctx.getFileManager().addSequenceOutput(so);

        return output;
    }

    private String getModelName(PipelineJob job, boolean asFile) throws PipelineJobException
    {
        String modelName = StringUtils.trimToNull(getProvider().getParameterByName("modelName").extractValue(job, getProvider(), getStepIdx(), String.class, null));
        if (modelName == null)
        {
            throw new PipelineJobException("Missing modelName param");
        }

        if (asFile)
        {
            modelName = FileUtil.makeLegalName(modelName) + ".pkl";
        }

        return modelName;
    }

    @Override
    protected Chunk createParamChunk(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Chunk ret = super.createParamChunk(ctx, inputObjects, outputPrefix);
        String modelName = getModelName(ctx.getJob(), true);
        ret.bodyLines.add("modelFile <- '/work/" + modelName + "'");

        return ret;
    }
}