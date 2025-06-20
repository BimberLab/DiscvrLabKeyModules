package org.labkey.api.studies.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractEventProvider implements EventProvider
{
    private final String _name;
    private final String _label;
    private final String _description;
    private final Module _owner;

    public AbstractEventProvider(String name, String label, String description, Module owner)
    {
        _name = name;
        _label = label;
        _description = description;
        _owner = owner;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public String getLabel()
    {
        return _label;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public boolean isAvailable(Container c)
    {
        return c.getActiveModules().contains(_owner);
    }

    @Override
    public final Map<String, Date> inferDates(Collection<String> subjectList, Container c, User u)
    {
        Map<String, Date> result = new HashMap<>(inferDatesRaw(subjectList, c, u));
        subjectList.forEach(x -> {
            if (!result.containsKey(x))
            {
                result.put(x, null);
            }
        });

        return result;
    }

    abstract protected Map<String, Date> inferDatesRaw(Collection<String> subjectList, Container c, User u);

    protected @Nullable TableInfo getTable(Container c, User u, String schema, String table)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, schema);
        if (us == null)
        {
            return null;
        }

        TableInfo ti = us.getTable("assignment");
        if (ti == null || !ti.hasPermission(u, ReadPermission.class))
        {
            return null;
        }

        return ti;
    }
}
