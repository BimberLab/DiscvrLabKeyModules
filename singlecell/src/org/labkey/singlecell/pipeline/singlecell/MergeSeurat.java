package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class MergeSeurat extends AbstractCellMembraneStep
{
    public MergeSeurat(PipelineContext ctx, MergeSeurat.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("MergeSeurat", "Merge Seurat Objects", "CellMembrane/Seurat", "This will merge the incoming seurat objects into a single object, merging all assays. Note: this will discard any normalization or DimRedux data, and performs zero validation to ensure this is compatible with downstream steps.", Arrays.asList(
                    SeuratToolParameter.create("projectName", "New Dataset Name", "The updated baseline for this merged object.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("doNotIncludeInTemplates", true);
                    }}, null),
                    SeuratToolParameter.create("doDiet", "Run DietSeurat", "If selected, this will run DietSeurat and gc() on the incoming seurat objects prior to merge. This is primarily to help with memory.", "checkbox", null, false, "dietSeurat", true, false),
                    SeuratToolParameter.create("errorOnBarcodeSuffix", "Error On Cell Barcode Suffixes", "In certain cases, software appends a digit (i.e. -1) to the end of cell barcodes; however, no code in DISCVR should do this. These are a problem if different datasets are inconsistent when using them.  If this setting is checked, the code will error if these are encountered.", "checkbox", null, true, "errorOnBarcodeSuffix", true, false),
                    SeuratToolParameter.create("assaysToDrop", "Assays to Drop", "These assays, entered comma-separated or one/line, will be dropped prior to merge.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/(^['\"]+)|(['\"]+$)/g");
                    }}, "RNA.orig").delimiter(",")
            ), null, null);
        }

        @Override
        public MergeSeurat create(PipelineContext ctx)
        {
            return new MergeSeurat(ctx, this);
        }
    }

    @Override
    public boolean isIncluded(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputs) throws PipelineJobException
    {
        return inputs.size() > 1;
    }

    @Override
    public String getFileSuffix()
    {
        return "merge";
    }
}
