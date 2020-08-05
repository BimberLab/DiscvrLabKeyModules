package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Created by bimber on 6/29/2017.
 */
public class CollectWgsMetricsWithNonZeroCoverageWrapper extends CollectWgsMetricsWrapper
{
    public CollectWgsMetricsWithNonZeroCoverageWrapper(Logger log)
    {
        super(log);
    }

    protected String getToolName()
    {
        return "CollectWgsMetricsWithNonZeroCoverage";
    }
}
