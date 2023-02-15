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

public class AvgExpression extends AbstractRDiscvrStep
{
    public AvgExpression(PipelineContext ctx, AvgExpression.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("AvgExpression", "Pseudobulk", "CellMembrane/Seurat", "This will run Pseudobulking on the raw counts, grouping using the provided fields. It will generate a seurat object with sum of counts per group/feature.", Arrays.asList(
                    SeuratToolParameter.create("groupFields", "Grouping Field(s)", "This field will be used to group cells of the seurat object. For each unique value of this field, count averages will be computed and saved into a matrix with one column per group. Any cells lacking a value in this field will be discarded.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, "cDNA_ID").delimiter(","),
                    SeuratToolParameter.create("addMetadata", "Query Metadata?", "If checked, Rdiscvr::QueryAndApplyMetadataUsingCDNA will be run after aggregation. This requires a cDNA_ID column to exist.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("assayName", "Assay Name", "The assay to use", "textfield", new JSONObject(){{

                    }}, "RNA")
            ), null, null);
        }

        @Override
        public AvgExpression create(PipelineContext ctx)
        {
            return new AvgExpression(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "avg";
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return true;
    }

    @Override
    public Collection<String> getRLibraries()
    {
        Set<String> ret = new HashSet<>();
        ret.add("Seurat");
        ret.add("dplyr");
        ret.addAll(super.getRLibraries());

        return ret;
    }
}

