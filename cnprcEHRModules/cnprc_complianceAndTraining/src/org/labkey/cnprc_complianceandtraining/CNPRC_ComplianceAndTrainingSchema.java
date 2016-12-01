package org.labkey.cnprc_complianceandtraining;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

public class CNPRC_ComplianceAndTrainingSchema
{

    private static final CNPRC_ComplianceAndTrainingSchema _instance = new CNPRC_ComplianceAndTrainingSchema();
    public static final String SCHEMA_NAME = "cnprc_complianceAndTraining";

    public static CNPRC_ComplianceAndTrainingSchema getInstance()
    {
        return _instance;
    }

    private CNPRC_ComplianceAndTrainingSchema()
    {
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
