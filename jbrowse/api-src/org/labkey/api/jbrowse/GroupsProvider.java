package org.labkey.api.jbrowse;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

public interface GroupsProvider
{
        @Nullable List<String> getGroupMembers(String trackId, String groupName, Container c, User u);

        @Nullable List<String> getGroupNames(Container c, User u);

        boolean isAvailable(Container c, User u);
}