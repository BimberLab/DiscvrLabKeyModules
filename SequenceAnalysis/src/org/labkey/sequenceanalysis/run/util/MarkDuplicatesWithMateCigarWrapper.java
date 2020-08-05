package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Created by bimber on 5/5/2016.
 */
public class MarkDuplicatesWithMateCigarWrapper extends MarkDuplicatesWrapper
{
    public MarkDuplicatesWithMateCigarWrapper(Logger log)
    {
        super(log);
    }

    @Override
    protected String getToolName()
    {
        return "MarkDuplicatesWithMateCigar";
    }
}
