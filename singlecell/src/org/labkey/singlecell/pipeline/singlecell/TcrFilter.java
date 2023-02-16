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
            super("TcrFilter", "TCR-Based Filter", "CellMembrane/Seurat", "This will run standard Seurat processing steps to normalize and scale the data.", Collections.singletonList(
                    SeuratToolParameter.create("cdr3s", "CDR3s To Keep", "A comma- or newline-delimited list of CDR3 sequences where locus prefixes the AA sequence (i.e. TRB:XXXXXX or TRA:YYYYYYY). Any cell matching any of these CDR3s will be kept. If that cell has multiple chains for a locus (i.e. 'CASSXXXXX,CASSYYYYY'), then only one of these needs to match for that cell to be kept. Also, all the input CDR3s should be single-chain (i.e. 'TRA:XXXXX', not 'TRA:XXXX,YYYY').", "sequenceanalysis-trimmingtextarea", new JSONObject()
                    {{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/(^['\"]+)|(['\"]+$)/g");
                    }}, null).delimiter(",")
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


