package org.labkey.sequenceanalysis.api.pipeline;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:13 PM
 */
public interface VariantCallingStep extends PipelineStep
{
    public PipelineStepOutput callVariants(File inputBam, File refFasta);
}
