package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class NormalizeAndScale extends AbstractCellMembraneStep
{
    public NormalizeAndScale(PipelineContext ctx, NormalizeAndScale.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("NormalizeAndScale", "Normalize/Scale", "OOSAP", "This will run standard Seurat processing steps to normalize and scale the data.", Arrays.asList(
                    SeuratToolParameter.create("variableFeatureSelectionMethod", "Variable Feature Selection Method", "The value, passed directly to Seurat's FindVariableFeatures, variableFeatureSelectionMethod", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "vst");
                        put("initialValues", "vst");
                        put("allowBlank", false);
                    }}, "vst"),
                    SeuratToolParameter.create("blockSize", "Block Size", "This will be passed to block.size in Seurat::ScaleData, which determines the number of features processed as a time. Increasing might increase speed at a memory cost, and descreasing on large datasets might reduce memory at a cost of overall speed.", "ldk-integerfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 150);
                        put("delimiter", ",");
                    }}, 1000, "block.size", true)
            ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public NormalizeAndScale create(PipelineContext ctx)
        {
            return new NormalizeAndScale(ctx, this);
        }
    }
}


