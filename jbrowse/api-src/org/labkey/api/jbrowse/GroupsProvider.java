package org.labkey.api.jbrowse;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Set;

public interface GroupsProvider
{
        @Nullable Set<String> getGroupMembers(String groupName, Container c, User u);
        boolean hasGroup(String groupName, Container c, User u);
        boolean isAvailable(Container c, User u);
}