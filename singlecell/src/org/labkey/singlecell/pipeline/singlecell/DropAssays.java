package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class DropAssays extends AbstractRiraStep
{
    public DropAssays(PipelineContext ctx, DropAssays.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("DropAssays", "Assay(s) to Drop", "Seurat", "This will drop the selected assays from your Seurat object(s)", Arrays.asList(
                SeuratToolParameter.create("assayNames", "Assay(s)", "The names of assays to drop, such as: RNA.orig, ADT, or RNA", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                    put("allowBlank", false);
                    put("height", 150);
                    put("delimiter", ",");
                    put("stripCharsRe", "/['\"]/g");
                }}, null).delimiter(",")
            ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public DropAssays create(PipelineContext ctx)
        {
            return new DropAssays(ctx, this);
        }
    }
}
