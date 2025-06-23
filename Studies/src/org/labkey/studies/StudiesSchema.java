package org.labkey.studies;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

public class StudiesSchema
{
    private static final StudiesSchema _instance = new StudiesSchema();
    public static final String NAME = "studies";

    public static final String TABLE_STUDIES = "studies";
    public static final String TABLE_COHORTS = "studyCohorts";
    public static final String TABLE_ANCHOR_EVENTS = "anchorEvents";
    public static final String TABLE_EXPECTED_TIMEPOINTS = "expectedTimepoints";
    public static final String TABLE_TIMEPOINT_TO_DATE = "timepointToDate";

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
