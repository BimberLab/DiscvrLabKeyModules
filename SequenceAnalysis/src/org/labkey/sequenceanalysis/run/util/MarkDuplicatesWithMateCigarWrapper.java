package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;

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
