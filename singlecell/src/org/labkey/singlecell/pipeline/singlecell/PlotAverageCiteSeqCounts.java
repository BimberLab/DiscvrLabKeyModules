package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Arrays;
import java.util.Collection;

public class PlotAverageCiteSeqCounts extends AbstractCellMembraneStep
{
    public PlotAverageCiteSeqCounts(PipelineContext ctx, PlotAverageCiteSeqCounts.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("PlotAverageCiteSeqCounts", "Plot Average Cite-Seq Counts", "CellMembrane", "This will generate a heatmap with average ADT counts, grouped using the fields below.", Arrays.asList(
                    SeuratToolParameter.create("fieldNames", "Fields To Plot", "Enter one field name per line", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 150);
                        put("delimiter", ",");
                    }}, "ClusterNames_0.2,ClusterNames_0.4,ClusterNames_0.6").delimiter(",")
            ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public PlotAverageCiteSeqCounts create(PipelineContext ctx)
        {
            return new PlotAverageCiteSeqCounts(ctx, this);
        }
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("Seurat");
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return false;
    }

    @Override
    public String getFileSuffix()
    {
        return "avgAdt";
    }
}
