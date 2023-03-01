package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class CellBarcodeFilter extends AbstractCellMembraneStep
{
    public CellBarcodeFilter(PipelineContext ctx, Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CellBarcodeFilter", "CellBarcode Filter", "CellMembrane/Seurat", "This will filter a seurat object based on cell barcodes.", Arrays.asList(
                    SeuratToolParameter.create("cellbarcodesToDrop", "Cell Barocdes To Drop", "A comma- or newline-delimited list of complete cell barcodes to drop.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/(^['\"]+)|(['\"]+$)/g");
                    }}, null).delimiter(",")
            ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public CellBarcodeFilter create(PipelineContext ctx)
        {
            return new CellBarcodeFilter(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "cellbarcodeFilter";
    }
}