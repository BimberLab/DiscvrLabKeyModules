package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class Downsample extends AbstractCellMembraneStep
{
    public Downsample(PipelineContext ctx, Downsample.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("Downsample", "Downsample Cells", "CellMembrane/Seurat", "This will downsample cells from the input object(s) based on the parameters below. Downsampling will be applied independently to each incoming Seurat object. If a second field is provided, cells within each object will be subset using that field, and then downsampled.", Arrays.asList(
                    SeuratToolParameter.create("targetCells", "Target Cells Per Unit of Data", "Each unit of data will be downsampled to this level", "ldk-integerfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null),
                    SeuratToolParameter.create("subsetFields", "Additional Grouping Fields", "A comma-separated list of fields that will be used to subset data within each seurat object. For example, if 'BarcodePrefix' is provided and 500 target cells is selected, each incoming seurat object will be subset to no more than 500 cells per unique value of BarcodePrefix. If blank, each object will be treated as one unit of data.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, null, "subsetFields", true, true).delimiter(","),
                    SeuratToolParameter.create("seed", "Random Seed", "This random seed, used for downsampling", "ldk-integerfield", new JSONObject(){{

                    }}, 1234)
            ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public Downsample create(PipelineContext ctx)
        {
            return new Downsample(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "ds";
    }
}
