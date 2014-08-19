package org.labkey.sequenceanalysis.api.pipeline;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:19 PM
 */
public interface AnalysisStep extends PipelineStep
{
    /**
     * Optional.  Allows this analysis to gather any information from the server required to execute the analysis.  This information needs to be serialized
     * to run remotely, which could be as simple as writing to a text file.
     * @param models
     * @throws PipelineJobException
     */
    public void init(List<AnalysisModel> models) throws PipelineJobException;

    /**
     * @return
     * @throws PipelineJobException
     */
    public Output performAnalysisPerSample(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException;

    public void performAnalysisOnAll(List<Output> previousSteps);

    public interface Output
    {
        public List<File> getVcfFiles();
    }
}
