package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class SummarizeTCellActivation extends AbstractRDiscvrStep
{
    public SummarizeTCellActivation(PipelineContext ctx, SummarizeTCellActivation.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("SummarizeTCellActivation", "Summarize TCell Activation", "RDiscvr", "This uses Rdiscvr::SummarizeTCellActivation to summarize cells with activation above a threshold.", Arrays.asList(
                    SeuratToolParameter.create("groupingFields", "Grouping Field(s)", "The fields used to group cells.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 200);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, "Stim,Population,Tissue,SampleDate,AssayType").delimiter(","),
                    SeuratToolParameter.create("threshold", "Threshold", "Cells with a score greater than or equal to this are considered activated", "ldk-numberfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 2);
                    }}, 0.5),
                    SeuratToolParameter.create("xFacetField", "X Facet Field", "A field used to facet the resulting plot", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, "Population"),
                    SeuratToolParameter.create("activationFieldName", "Activation Field Name", "The name of the field holding the score used to activation thresholding", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, "TandNK_Activation_UCell")
            ), null, null);
        }

        @Override
        public SummarizeTCellActivation create(PipelineContext ctx)
        {
            return new SummarizeTCellActivation(ctx, this);
        }
    }

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Output output = super.execute(ctx, inputObjects, outputPrefix);
        File[] outputs = ctx.getOutputDir().listFiles(f -> !f.isDirectory() && f.getName().endsWith(".activation.txt"));
        if (outputs == null || outputs.length == 0)
        {
            throw new PipelineJobException("No outputs found for SummarizeTCellActivation");
        }

        for (File fn : outputs)
        {
            output.addSequenceOutput(fn, "TCell Activation: " + inputObjects.get(0).getDatasetName(), "TCell Activation", inputObjects.get(0).getReadsetId(), null, ctx.getSequenceSupport().getCachedGenomes().iterator().next().getGenomeId(), null);
        }

        return output;
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return false;
    }

    @Override
    public String getFileSuffix()
    {
        return "activation";
    }
}

