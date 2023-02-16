package org.labkey.sequenceanalysis.run.reference;

import org.labkey.api.sequenceanalysis.pipeline.DefaultPipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceLibraryStep;

/**
 * User: bimber
 * Date: 6/21/2014
 * Time: 11:14 AM
 */
public class ReferenceLibraryOutputImpl extends DefaultPipelineStepOutput implements ReferenceLibraryStep.Output
{
    private final ReferenceGenome _referenceGenome;

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
