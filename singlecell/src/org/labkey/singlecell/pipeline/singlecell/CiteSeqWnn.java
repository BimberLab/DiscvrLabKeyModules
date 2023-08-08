package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.List;

public class CiteSeqWnn extends AbstractCellMembraneStep
{
    public CiteSeqWnn(PipelineContext ctx, CiteSeqWnn.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CiteSeqWnn", "Seurat WNN", "Seurat", "This will run DimRedux steps on the ADT data.", List.of(
                    SeuratToolParameter.create("assayName", "Assay Name", "The assay to use", "textfield", new JSONObject(){{

                    }}, "ADT")
            ), null, null);
        }

        @Override
        public CiteSeqWnn create(PipelineContext ctx)
        {
            return new CiteSeqWnn(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "wnn";
    }
}
