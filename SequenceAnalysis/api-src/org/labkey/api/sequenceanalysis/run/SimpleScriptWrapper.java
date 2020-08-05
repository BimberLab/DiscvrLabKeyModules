package org.labkey.api.sequenceanalysis.run;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;

/**
 * Created by bimber on 6/16/2016.
 */
public class SimpleScriptWrapper extends AbstractCommandWrapper
{
    public SimpleScriptWrapper(@Nullable Logger logger)
    {
        super(logger);
    }
}
