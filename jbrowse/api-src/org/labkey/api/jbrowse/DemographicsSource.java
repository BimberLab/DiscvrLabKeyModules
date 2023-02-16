package org.labkey.api.jbrowse;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface DemographicsSource
{
    Map<String, Map<String, Object>> resolveSubjects(List<String> subjects, Container c, User u);

    LinkedHashMap<String, String> getFields();

    boolean isAvailable(Container c, User u);
}
