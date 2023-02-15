package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class CiteSeqPlots extends AbstractCellMembraneStep
{
    public CiteSeqPlots(PipelineContext ctx, CiteSeqPlots.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CiteSeqPlots", "CiteSeq/ADT Plots", "CellMembrane/Seurat", "This will create FeaturePlots for all features in the ADT assay.", Arrays.asList(
                    SeuratToolParameter.create("assayName", "Assay Name", "The assay to use", "textfield", new JSONObject(){{

                    }}, "ADT")
            ), null, null);
        }

        @Override
        public CiteSeqPlots create(PipelineContext ctx)
        {
            return new CiteSeqPlots(ctx, this);
        }
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return false;
    }

    @Override
    public String getFileSuffix()
    {
        return "cite-plots";
    }
}
