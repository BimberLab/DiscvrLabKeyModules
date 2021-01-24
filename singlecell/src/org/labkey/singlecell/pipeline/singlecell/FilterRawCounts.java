package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class FilterRawCounts extends AbstractCellMembraneStep
{
    public FilterRawCounts(PipelineContext ctx, FilterRawCounts.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("FilterRawCounts", "Filter Raw Counts", "Seurat/OOSAP", "This will use OOSAP/Seurat to perform basic filtering on cells based on UMI count, feature count, etc.", Arrays.asList(
                    SeuratToolParameter.create("nCountRnaLow", "Min UMI Count", "Cells with UMI counts below this value will be discarded", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 0, "nCount_RNA.low"),
                    SeuratToolParameter.create("nCountRnaHigh", "Max UMI Count", "Cells with UMI counts above this value will be discarded", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 20000, "nCount_RNA.high"),
                    SeuratToolParameter.create("nCountFeatureLow", "Min Feature Count", "Cells with unique feature totals below this value will be discarded", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 200, "nFeature.low"),
                    SeuratToolParameter.create("nCountFeatureHigh", "Max Feature Count", "Cells with unique feature totals above this value will be discarded", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 5000, "nFeature.high"),
                    SeuratToolParameter.create("pMitoLow", "Min Percent Mito", "Cells percent mitochondrial genes below this value will be discarded", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                    }}, 0, "pMito.low"),
                    SeuratToolParameter.create("pMitoHigh", "Max Percent Mito", "Cells percent mitochondrial genes above this value will be discarded", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                    }}, 0.15, "pMito.high")
                    ), null, null);
        }


        @Override
        public FilterRawCounts create(PipelineContext ctx)
        {
            return new FilterRawCounts(ctx, this);
        }
    }
}
