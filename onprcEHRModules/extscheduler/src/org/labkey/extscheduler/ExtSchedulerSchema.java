package org.labkey.extscheduler;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;

public class ExtSchedulerSchema
{
    public static final String NAME = "extscheduler";

    private static final ExtSchedulerSchema _instance = new ExtSchedulerSchema();

    public static ExtSchedulerSchema getInstance()
    {
        return _instance;
    }

    private ExtSchedulerSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.extscheduler.ExtSchedulerSchema.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get("extscheduler", DbSchemaType.Module);
    }
}
