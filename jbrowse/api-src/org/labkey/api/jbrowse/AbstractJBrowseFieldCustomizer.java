package org.labkey.api.jbrowse;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;

abstract public class AbstractJBrowseFieldCustomizer implements JBrowseFieldCustomizer
{
    private final Module _module;
    public AbstractJBrowseFieldCustomizer(Module owningModule)
    {
        _module = owningModule;
    }

    @Override
    public boolean isAvailable(Container c, User u)
    {
        return c.getActiveModules().contains(_module);
    }
}