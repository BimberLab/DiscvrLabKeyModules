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
                    SeuratToolParameter.create("nCountRnaStratification", "Perform nCountRna Stratification?", "A boolean determining whether or not automatic outlier detection of clusters with abnormal nCount_RNA should be detected", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    SeuratToolParameter.create("assayName", "Assay Name", "The assay to use", "textfield", new JSONObject(){{

                    }}, "RNA"),
                    SeuratToolParameter.create("additionalFieldsToAggregate", "Additional Field(s) to Aggregate", "Each field in this list must match a meta.data field. That field will be aggregated per group, and the mean reported in meta.data of the pseudobulk output object. Note: this also supports wildcards (which must contain asterisk). Any term containing asterisk will be interpreted as a regex and passed to grep() to find matching meta.data fields.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", true);
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, "*UCell$").delimiter(",")
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

