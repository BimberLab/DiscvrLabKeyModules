package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.singlecell.pipeline.AbstractSingleCellPipelineStep;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;

abstract public class AbstractCellMembraneStep extends AbstractSingleCellPipelineStep
{
    public static String CONTAINER_NAME = "ghcr.io/bimberlabinternal/cellmembrane:latest";

    public AbstractCellMembraneStep(PipelineStepProvider<?> provider, PipelineContext ctx)
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
        return CONTAINER_NAME;
    }

    // NOTE: ExperimentHub and similar packages default to saving data to the user's home dir. Set a directory, to avoid issues when not running the container as root
    @Override
    public String getDockerHomeDir()
    {
        return "/dockerHomeDir";
    }
}
