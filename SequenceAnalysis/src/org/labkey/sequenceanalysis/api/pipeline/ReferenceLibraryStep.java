package org.labkey.sequenceanalysis.api.pipeline;

import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:11 PM
 */
public interface ReferenceLibraryStep extends PipelineStep
{
    public Output createReferenceFasta(File outputDirectory) throws PipelineJobException;

    public static interface Output extends PipelineStepOutput
    {
        public File getReferenceFasta() throws PipelineJobException;

        public ReferenceGenome getReferenceGenome();
    }
}
