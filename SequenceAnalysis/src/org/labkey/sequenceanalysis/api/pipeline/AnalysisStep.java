package org.labkey.sequenceanalysis.api.pipeline;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;

import java.io.File;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:19 PM
 */
public interface AnalysisStep extends PipelineStep
{
    /**
     * @return
     * @throws org.labkey.api.pipeline.PipelineJobException
     */
    public void performAnalysis(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException;
}
