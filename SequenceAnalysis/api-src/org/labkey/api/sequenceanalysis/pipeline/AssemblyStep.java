package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.Pair;

import java.io.File;

/**
 * Created by bimber on 2/10/2016.
 */
public interface AssemblyStep extends PipelineStep
{
    Output performAssembly(Readset rs, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename) throws PipelineJobException;

    interface Output extends PipelineStepOutput
    {

    }
}
