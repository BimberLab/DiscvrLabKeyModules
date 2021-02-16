package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class CiteSeqPca extends AbstractCellMembraneStep
{
    public CiteSeqPca(PipelineContext ctx, CiteSeqPca.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CiteSeqPca", "CiteSeq/ADT PCA", "CellMembrane/Seurat", "This will run PCA on the ADT data.", Arrays.asList(
                    SeuratToolParameter.create("performClrNormalization", "Perform CLR Normalization", "If true, Seurat CLR normalization will be performed. Otherwise any pre-existing normalization is used.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true, "performClrNormalization", true)
            ), null, null);
        }

        @Override
        public CiteSeqPca create(PipelineContext ctx)
        {
            return new CiteSeqPca(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "cite-pca";
    }
}
