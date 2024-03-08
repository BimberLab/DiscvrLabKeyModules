package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.Collections;

public class RunCsCore extends AbstractCellMembraneStep
{
    public RunCsCore(PipelineContext ctx, Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunCsCore", "CS-CORE", "CS-CORE", "Run CS-CORE on the seurat object to identify gene modules.", Collections.emptyList(), null, null);
        }

        @Override
        public RunCsCore create(PipelineContext ctx)
        {
            return new RunCsCore(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "cscore";
    }
}