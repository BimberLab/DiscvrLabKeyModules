package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TcrFilter extends AbstractCellMembraneStep
{
    public TcrFilter(PipelineContext ctx, TcrFilter.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("TcrFilter", "TCR-Based Filter", "CellMembrane/Seurat", "This will filter a seurat object based on TCR data.", Arrays.asList(
                    SeuratToolParameter.create("cdr3s", "CDR3s To Keep", "A comma- or newline-delimited list of CDR3 sequences where locus prefixes the AA sequence (i.e. TRB:XXXXXX or TRA:YYYYYYY). Any cell matching any of these CDR3s will be kept. If that cell has multiple chains for a locus (i.e. 'CASSXXXXX,CASSYYYYY'), then only one of these needs to match for that cell to be kept. Also, all the input CDR3s should be single-chain (i.e. 'TRA:XXXXX', not 'TRA:XXXX,YYYY').", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/(^['\"]+)|(['\"]+$)/g");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("thresholdToKeep", "Min Cells To Keep", "If fewer than this many cells remain, the object will be discarded. This is primary present because seurat cannot easily accommodate objects with 1 cells", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 1)
            ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public TcrFilter create(PipelineContext ctx)
        {
            return new TcrFilter(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "tcrFilter";
    }
}


