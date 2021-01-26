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
            super("MergeSeurat", "Merge Seurat Objects", "OOSAP", "This will merge the incoming seurat objects into a single object, merging all assays. Note: this will discard any normalization or DimRedux data, and performs zero validation to ensure this is compatible with downstream steps.", Arrays.asList(
                    SeuratToolParameter.create("projectName", "New Dataset Name", "The updated baseline for this merged object.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null)
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

}
