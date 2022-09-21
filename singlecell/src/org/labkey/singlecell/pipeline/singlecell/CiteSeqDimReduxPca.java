package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class CiteSeqDimReduxPca extends AbstractCellMembraneStep
{
    public CiteSeqDimReduxPca(PipelineContext ctx, CiteSeqDimReduxPca.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CiteSeqDimReduxPca", "CiteSeq DimRedux (PCA)", "CellMembrane/Seurat", "This will run DimRedux steps on the ADT data, based on PCA.", Arrays.asList(
                    SeuratToolParameter.create("performClrNormalization", "Perform CLR Normalization", "If true, Seurat CLR normalization will be performed. Otherwise any pre-existing normalization is used.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true, "performClrNormalization", true),
                    SeuratToolParameter.create("adtWhitelist", "ADT Whitelist", "If provided, these ADTs will be scaled and used for PCA. If empty, all ADTs will be used.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("adtBlacklist", "ADT Blacklist", "If provided, these ADTs will be excluded from scaling/PCA", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null).delimiter(",")
                    ), null, null);
        }

        @Override
        public CiteSeqDimReduxPca create(PipelineContext ctx)
        {
            return new CiteSeqDimReduxPca(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "cite-pca";
    }
}
