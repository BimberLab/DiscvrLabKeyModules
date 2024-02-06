package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class ScoreCellCycle extends AbstractCellMembraneStep
{
    public ScoreCellCycle(PipelineContext ctx, ScoreCellCycle.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("ScoreCellCycle", "Score Cell Cycle", "CellMembrane/Seurat", "This will score cells by cell cycle phase.", Arrays.asList(
                    SeuratToolParameter.create("useAlternateG2M", "Use Alternate G2M Genes", "If checked, this will use a smaller set of G2M genes, defined from: https://raw.githubusercontent.com/hbc/tinyatlas/master/cell_cycle/Homo_sapiens.csv", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
            ), null, null);
        }

        @Override
        public ScoreCellCycle create(PipelineContext ctx)
        {
            return new ScoreCellCycle(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "cc";
    }
}

