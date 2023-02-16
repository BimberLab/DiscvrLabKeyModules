package org.labkey.api.sequenceanalysis.pipeline;

import org.labkey.api.sequenceanalysis.SequenceOutputFile;

import java.util.List;

/**
 * Created by bimber on 11/9/2016.
 */
public interface SequenceOutputTracker
{
    List<SequenceOutputFile> getOutputsToCreate();
}
