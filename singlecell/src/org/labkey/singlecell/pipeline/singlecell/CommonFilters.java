package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CommonFilters extends AbstractCellMembraneStep
{
    public CommonFilters(PipelineContext ctx, CommonFilters.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CommonFilters", "Common Filters", "CellMembrane/Seurat", "The seurat object will be subset based on the selected filters.", Arrays.asList(
                    SeuratToolParameter.create("saturation.RNA.min", "Saturation.RNA Min", "Saturation.RNA min value", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 3);
                    }}, null),
                    SeuratToolParameter.create("saturation.RNA.max", "Saturation.RNA Max", "Saturation.RNA max value", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 3);
                    }}, null),
                    SeuratToolParameter.create("saturation.ADT.min", "Saturation.ADT Min", "Saturation.ADT min value", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 3);
                    }}, null),
                    SeuratToolParameter.create("saturation.ADT.max", "Saturation.ADT Max", "Saturation.ADT max value", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 3);
                    }}, null),
                    SeuratToolParameter.create("dropHashingFail", "Drop Cells Without Hashing", "If checked, any cell that lacks hashing data will be dropped. All cells from a lane that did not use hashing will be included.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, null)
            ), null, null);
        }

        @Override
        public CommonFilters create(PipelineContext ctx)
        {
            return new CommonFilters(ctx, this);
        }
    }

    @Override
    public Collection<String> getRLibraries()
    {
        Set<String> ret = new HashSet<>();
        ret.add("Seurat");
        ret.addAll(super.getRLibraries());

        return ret;
    }

    @Override
    public String getFileSuffix()
    {
        return "cf";
    }
}
