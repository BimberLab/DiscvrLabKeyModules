package org.labkey.sequenceanalysis;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;

/**
 * User: bimber
 * Date: 6/24/2014
 * Time: 1:11 PM
 */
public class PipelineStartup
{
    private static final Logger _log = LogManager.getLogger(PipelineStartup.class);
    private static boolean _hasRegistered = false;

    public PipelineStartup()
    {
        if (_hasRegistered)
        {
            _log.warn("SequenceAnalysis resources have already been registered, skipping");
        }
        else
        {
            _log.info("Registering SequenceAnalysis resources");
            SequenceAnalysisService.setInstance(SequenceAnalysisServiceImpl.get());
            SequencePipelineService.setInstance(SequencePipelineServiceImpl.get());
            SequenceAnalysisModule.registerPipelineSteps();
            _hasRegistered = true;
        }
    }
}
