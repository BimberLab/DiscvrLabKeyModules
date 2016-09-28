package org.labkey.cnprc_pdl;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

/**
 * Created by Ron on 9/26/2016.
 */
public class CNPRC_PDLSchema
{
    private static final CNPRC_PDLSchema _instance = new CNPRC_PDLSchema();
    public static final String SCHEMA_NAME = "cnprc_pdl";

    public static CNPRC_PDLSchema getInstance()
    {
        return _instance;
    }

    private CNPRC_PDLSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.cnprc_pdl.cnprc_pdlSchema.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

}
