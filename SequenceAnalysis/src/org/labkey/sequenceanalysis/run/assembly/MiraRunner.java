package org.labkey.sequenceanalysis.run.assembly;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.pipeline.SequencePipelineSettings;

import java.io.File;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:16 PM
 */
public class MiraRunner extends AbstractPipelineStep
{
    public MiraRunner(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public void doDeNovoAssembly(File input, String outputPrefix, SequencePipelineSettings settings)
    {

    }

    public void doMappingAssembly(File input, String outputPrefix, SequencePipelineSettings settings, File referenceFasta)
    {

    }
}
