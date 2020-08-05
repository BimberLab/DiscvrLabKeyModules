package org.labkey.api.sequenceanalysis.run;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class AbstractDiscvrSeqWrapper extends AbstractGatk4Wrapper
{
    public AbstractDiscvrSeqWrapper(Logger log)
    {
        super(log);
    }

    @Override
    protected String getJarName()
    {
        return "DISCVRSeq.jar";
    }
}
