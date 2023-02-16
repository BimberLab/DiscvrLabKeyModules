package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class FindClustersAndDimRedux extends AbstractCellMembraneStep
{
    public FindClustersAndDimRedux(PipelineContext ctx, FindClustersAndDimRedux.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("FindClustersAndDimRedux", "Find Clusters And Dim Redux", "CellMembrane/Seurat", "This will run tSNE and UMAP for the input object.", List.of(
                    SeuratToolParameter.create("minDimsToUse", "Min. PCs to Use", "The minimum number of PCs to use", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 15)

            ), null, null);
        }


        @Override
        public FindClustersAndDimRedux create(PipelineContext ctx)
        {
            return new FindClustersAndDimRedux(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "dr";
    }
}
