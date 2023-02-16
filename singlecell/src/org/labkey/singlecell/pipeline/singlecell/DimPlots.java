package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DimPlots extends AbstractCellMembraneStep
{
    public DimPlots(PipelineContext ctx, DimPlots.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("DimPlots", "DimPlots", "Seurat", "This will generate DimPlots grouped by the variables below. Any variable not present is skipped.", Collections.singletonList(
                    SeuratToolParameter.create("fieldNames", "Fields To Plot", "Enter one field name per line", "sequenceanalysis-trimmingtextarea", new JSONObject()
                    {{
                        put("allowBlank", false);
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, null).delimiter(",")
            ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public DimPlots create(PipelineContext ctx)
        {
            return new DimPlots(ctx, this);
        }
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("Seurat", "patchwork");
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return false;
    }

    @Override
    public String getFileSuffix()
    {
        return "dimplot";
    }
}
