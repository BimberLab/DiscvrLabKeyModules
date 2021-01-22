package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.singlecell.pipeline.AbstractSingleCellPipelineStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;

abstract public class AbstractCellMembraneStep extends AbstractSingleCellPipelineStep
{
    public AbstractCellMembraneStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("CellMembrane");
    }

    @Override
    public String getDockerContainerName()
    {
        return "ghcr.io/bimberlabinternal/cellmembrane:latest";
    }
}
