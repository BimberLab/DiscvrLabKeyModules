package org.labkey.api.singlecell.pipeline;

import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

public interface SingleCellStep extends PipelineStep
{
    public static final String STEP_TYPE = "singleCell";

    public Collection<String> getLibraries();

    public void writeComments(PrintWriter writer, List<String> seuratVariables, SeuratContext ctx);

    public Output appendToMarkdown(PrintWriter writer, List<String> seuratVariables, SeuratContext ctx);

    public interface SeuratContext
    {

    }

    public static interface Output extends PipelineStepOutput
    {
        /**
         * Returns the cached seurat object
         */
        public File getOutput();
    }
}
