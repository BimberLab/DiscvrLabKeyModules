package org.labkey.api.jbrowse;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.List;

public interface JBrowseFieldCustomizer {
    public void customizeField(JBrowseFieldDescriptor field, Container c, User u);

    public List<String> getPromotedFilters(Collection<String> indexedFields, Container c, User u);

    public boolean isAvailable(Container c, User u);
}
