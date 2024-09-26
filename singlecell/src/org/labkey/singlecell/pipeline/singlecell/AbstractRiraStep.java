package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.singlecell.pipeline.AbstractSingleCellPipelineStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;

abstract public class AbstractRiraStep extends AbstractSingleCellPipelineStep
{
    public static String CONTAINER_NAME = "ghcr.io/bimberlab/rira:latest";

    public AbstractRiraStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("RIRA");
    }

    @Override
    public String getDockerContainerName()
    {
        return CONTAINER_NAME;
    }
}
