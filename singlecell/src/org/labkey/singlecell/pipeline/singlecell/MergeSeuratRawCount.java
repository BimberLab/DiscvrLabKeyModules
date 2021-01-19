package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class MergeSeuratRawCount extends AbstractOosapStep
{
    public MergeSeuratRawCount(PipelineContext ctx, MergeSeuratRawCount.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("MergeSeuratRawCount", "Merge ", "OOSAP", "If available, this will download and append CITE-seq data to the Seurat object(s).", Arrays.asList(

            ), null, null);
        }


        @Override
        public MergeSeuratRawCount create(PipelineContext ctx)
        {
            return new MergeSeuratRawCount(ctx, this);
        }
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}
