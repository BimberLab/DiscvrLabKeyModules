package org.labkey.jbrowse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PipelineStartup
{
    private static final Logger _log = LogManager.getLogger(PipelineStartup.class);
    private static boolean _hasRegistered = false;

    public PipelineStartup()
    {
        if (_hasRegistered)
        {
            _log.warn("JBrowse resources have already been registered, skipping");
        }
        else
        {
            _log.info("Registering JBrowse resources");
            JBrowseModule.registerPipelineSteps();
            _hasRegistered = true;
        }
    }
}
