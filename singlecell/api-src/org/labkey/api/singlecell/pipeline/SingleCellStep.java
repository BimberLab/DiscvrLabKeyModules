package org.labkey.api.singlecell.pipeline;

import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;

import java.io.File;
import java.util.Collection;
import java.util.List;

public interface SingleCellStep extends PipelineStep
{
    public static final String STEP_TYPE = "singleCell";
    public static final String SEURAT_PROCESSING = "seuratProcessing";

    public Collection<String> getRLibraries();

    public String getDockerContainerName();

    default void init(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputFiles)
    {

    }

    public Output execute(List<File> inputObjects, SeuratContext ctx);

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
