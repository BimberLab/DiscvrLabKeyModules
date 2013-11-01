package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collection;
import java.util.Collections;

/**
 * User: bimber
 * Date: 10/28/13
 * Time: 6:21 PM
 */
abstract public class AbstractAlignerWrapper extends AbstractCommandWrapper implements AlignerWrapper
{
    public AbstractAlignerWrapper(Logger logger)
    {
        super(logger);
    }

    public String getXtype()
    {
        return null;
    }

    public Collection<ClientDependency> getClientDependencies()
    {
        return Collections.emptySet();
    }
}
