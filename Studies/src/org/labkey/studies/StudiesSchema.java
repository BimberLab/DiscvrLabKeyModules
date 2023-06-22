package org.labkey.studies;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

public class StudiesSchema
{
    private static final StudiesSchema _instance = new StudiesSchema();
    public static final String NAME = "studies";

    public static StudiesSchema getInstance()
    {
        return _instance;
    }

    private StudiesSchema()
    {

    }

    public DbSchema getSchema()
    {
        return DbSchema.get(NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}
