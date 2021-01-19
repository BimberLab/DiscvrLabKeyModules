package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class SubsetSeurat extends AbstractOosapStep
{
    public SubsetSeurat(PipelineContext ctx, SubsetSeurat.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("SubsetSeurat", "Subset", "OOSAP", "The seurat object will be subset based on the expressions below, one per line, which are passed directly to Seurat::subset.", Arrays.asList(
                    ToolParameterDescriptor.create("expressions", "Expressions", "", "textbox", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null)
            ), null, null);
        }


        @Override
        public SubsetSeurat create(PipelineContext ctx)
        {
            return new SubsetSeurat(ctx, this);
        }
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}
