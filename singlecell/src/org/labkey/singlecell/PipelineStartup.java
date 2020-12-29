package org.labkey.singlecell;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            _log.warn("SingleCellModule resources have already been registered, skipping");
        }
        else
        {
            _log.info("Registering SingleCellModule resources");
            SingleCellModule.registerPipelineSteps();
            _hasRegistered = true;
        }
    }
}
