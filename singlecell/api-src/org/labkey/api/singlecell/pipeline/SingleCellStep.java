package org.labkey.api.singlecell.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public interface SingleCellStep extends AbstractSingleCellStep
{
    String STEP_TYPE = "singleCell";
}
