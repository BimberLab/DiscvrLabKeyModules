package org.labkey.api.sequenceanalysis.run;

import org.apache.log4j.Logger;

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
