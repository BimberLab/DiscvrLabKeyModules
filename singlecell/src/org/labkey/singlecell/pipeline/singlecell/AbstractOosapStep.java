package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.singlecell.pipeline.AbstractSingleCellPipelineStep;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;

abstract public class AbstractOosapStep extends AbstractSingleCellPipelineStep
{
    public AbstractOosapStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("OOSAP");
    }

    @Override
    public String getDockerContainerName()
    {
        return "ghcr.io/bimberlabinternal/oosap:latest";
    }
}
