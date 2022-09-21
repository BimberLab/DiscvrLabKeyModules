package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class PlotAssayFeatures extends AbstractCellMembraneStep
{
    public PlotAssayFeatures(PipelineContext ctx, PlotAssayFeatures.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("PlotAssayFeatures", "Plot Assay Features", "CellMembrane/Seurat", "This will create FeaturePlots for all features in the selected assays.", Arrays.asList(
                    SeuratToolParameter.create("assayNames", "Assay Name(s)", "The name(s) of the asays to plot.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                        put("allowBlank", false);
                    }}, null).delimiter(",")
            ), null, null);
        }

        @Override
        public PlotAssayFeatures create(PipelineContext ctx)
        {
            return new PlotAssayFeatures(ctx, this);
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
