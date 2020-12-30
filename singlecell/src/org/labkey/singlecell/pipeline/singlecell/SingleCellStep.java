package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

public interface SingleCellStep extends PipelineStep
{
    public Collection<String> getLibraries();

    public void writeComments(PrintWriter writer, List<String> seuratVariables, SeuratContext ctx);

    public Output appendToMarkdown(PrintWriter writer, List<String> seuratVariables, SeuratContext ctx);

    public interface Output
    {

    }

    public interface SeuratContext
    {

    }
}
