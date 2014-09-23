package org.labkey.sequenceanalysis.run.reference;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.pipeline.DefaultPipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceGenome;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceLibraryStep;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryTask;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/21/2014
 * Time: 11:14 AM
 */
public class ReferenceLibraryOutputImpl extends DefaultPipelineStepOutput implements ReferenceLibraryStep.Output
{
    private ReferenceGenome _referenceGenome;

    public ReferenceLibraryOutputImpl(ReferenceGenome referenceGenome)
    {
        _referenceGenome = referenceGenome;
    }

    @Override
    public ReferenceGenome getReferenceGenome()
    {
        return _referenceGenome;
    }
}
