package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.singlecell.pipeline.AbstractSingleCellPipelineStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;

abstract public class AbstractRDiscvrStep extends AbstractSingleCellPipelineStep
{
    public static String CONTAINER_NAME = "ghcr.io/bimberlabinternal/rdiscvr:latest";

    public AbstractRDiscvrStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("Rdiscvr");
    }

    @Override
    public String getDockerContainerName()
    {
        return CONTAINER_NAME;
    }
}
