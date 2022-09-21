package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class CiteSeqDimReduxDist extends AbstractCellMembraneStep
{
    public CiteSeqDimReduxDist(PipelineContext ctx, CiteSeqDimReduxDist.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CiteSeqDimReduxDist", "CiteSeq DimRedux (distance)", "CellMembrane/Seurat", "This will run DimRedux steps on the ADT data, based on euclidian distance.", Arrays.asList(
                    SeuratToolParameter.create("performClrNormalization", "Perform CLR Normalization", "If true, Seurat CLR normalization will be performed. Otherwise any pre-existing normalization is used.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true, "performClrNormalization", true)
            ), null, null);
        }

        @Override
        public CiteSeqDimReduxDist create(PipelineContext ctx)
        {
            return new CiteSeqDimReduxDist(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "cite-dist";
    }
}
