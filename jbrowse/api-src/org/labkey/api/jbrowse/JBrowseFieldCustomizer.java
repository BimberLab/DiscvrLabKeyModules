package org.labkey.api.jbrowse;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

public interface JBrowseFieldCustomizer {
    public void customizeField(JBrowseFieldDescriptor field);

    public boolean isAvailable(Container c, User u);
}
