package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.pipeline.AbstractSingleCellPipelineStep;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class RunCellHashing extends AbstractSingleCellPipelineStep
{
    public RunCellHashing(PipelineContext ctx, RunCellHashing.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunCellHashing", "Run/Store Cell Hashing", "cellhashR", "If available, this will run cellhashR to score cells by sample.", CellHashingService.get().getDefaultHashingParams(false), null, null);
        }

        @Override
        public RunCellHashing create(PipelineContext ctx)
        {
            return new RunCellHashing(ctx, this);
        }
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("cellhashR");
    }

    @Override
    public String getDockerContainerName()
    {
        return "ghcr.io/bimberlab/cellhashr:latest";
    }

    @Override
    public boolean requiresHashingOrCiteSeq()
    {
        return true;
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}
