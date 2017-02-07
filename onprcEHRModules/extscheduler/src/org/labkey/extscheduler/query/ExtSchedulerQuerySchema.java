package org.labkey.extscheduler.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.extscheduler.ExtSchedulerSchema;
import org.springframework.validation.BindException;

import java.util.Set;

public class ExtSchedulerQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "extscheduler";
    public static final String SCHEMA_DESCR = "Contains data about Ext Scheduler resources and events.";
    public static final String RESOURCES_TABLE_NAME = "Resources";
    public static final String EVENTS_TABLE_NAME = "Events";

    public ExtSchedulerQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, ExtSchedulerSchema.getInstance().getSchema());
    }

    @Override
    public Set<String> getTableNames()
    {
        return PageFlowUtil.set(RESOURCES_TABLE_NAME, EVENTS_TABLE_NAME);
    }

    @Override
    public TableInfo createTable(String name)
    {
        if (RESOURCES_TABLE_NAME.equalsIgnoreCase(name))
            return new ResourcesTable(ExtSchedulerSchema.getInstance().getSchema().getTable(RESOURCES_TABLE_NAME), this);
        if (EVENTS_TABLE_NAME.equalsIgnoreCase(name))
            return new EventsTable(ExtSchedulerSchema.getInstance().getSchema().getTable(EVENTS_TABLE_NAME), this);

        return null;
    }

    @Override
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        // Enable this block if you want the Events table to show edit link for non-Admins for
        // future events which the users is the "owner"
        //if (EVENTS_TABLE_NAME.equalsIgnoreCase(settings.getQueryName()))
        //{
        //    return new EventsQueryView(this, settings, errors);
        //}

        return super.createView(context, settings, errors);
    }
}