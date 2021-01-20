package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class SeuratDimRedux extends AbstractOosapStep
{
    public SeuratDimRedux(PipelineContext ctx, SeuratDimRedux.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("SeuratDimRedux", "Seurat DimRedux", "OOSAP", "This will run tSNA and UMAP for the input object.", Arrays.asList(
                    ToolParameterDescriptor.create("minDimsToUse", "Min. PCs to Use", "The minimum number of PCs to use", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 15)

            ), null, null);
        }


        @Override
        public SeuratDimRedux create(PipelineContext ctx)
        {
            return new SeuratDimRedux(ctx, this);
        }
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}
