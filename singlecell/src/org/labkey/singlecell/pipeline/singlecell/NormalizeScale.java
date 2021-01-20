package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class NormalizeScale extends AbstractOosapStep
{
    public NormalizeScale(PipelineContext ctx, NormalizeScale.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("NormalizeScale", "Normalize/Scale", "OOSAP", "This will run standard Seurat processing steps to normalize and scale the data.", Arrays.asList(
                    ToolParameterDescriptor.create("variableFeatureSelectionMethod", "Variable Feature Selection Method", "The value, passed directly to Seurat's FindVariableFeatures, variableFeatureSelectionMethod", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "vst");
                        put("initialValues", "vst");
                        put("allowBlank", false);
                    }}, "vst"),
                    ToolParameterDescriptor.create("toRegress", "Variables to Regress", "These will be passed to Seurat::ScaleData. Enter comma-separated or one field name per line", "textarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 150);
                    }}, "nCount_RNA,p.mito")
            ), null, null);
        }


        @Override
        public NormalizeScale create(PipelineContext ctx)
        {
            return new NormalizeScale(ctx, this);
        }
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}


