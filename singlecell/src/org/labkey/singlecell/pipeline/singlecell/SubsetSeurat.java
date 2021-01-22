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
            super("SubsetSeurat", "Subset", "OOSAP", "The seurat object will be subset based on the expressions below, one per line, which are passed directly to Seurat's subset(subset = X).", Arrays.asList(
                    ToolParameterDescriptor.create("expressions", "Expressions", "Enter one expression per line", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null)
            ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public SubsetSeurat create(PipelineContext ctx)
        {
            return new SubsetSeurat(ctx, this);
        }
    }
}
