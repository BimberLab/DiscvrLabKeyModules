package org.labkey.api.studies.study;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Each study will have a handful of important dates, which are used to define relative dates for each subject/participant.
 * The EventProvider classes provide a code-based way to establish the handful of critical dates. This code is executed to populate
 * the KeyEvents table, which maps subject/event to date.
 */
public interface EventProvider
{
    boolean isAvailable(Container c);

    String getName();

    String getLabel();

    String getDescription();

    Map<String, Date> inferDates(Collection<String> subjectList, Container c, User u);
}
