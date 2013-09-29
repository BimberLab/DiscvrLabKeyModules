package org.labkey.laboratory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.laboratory.query.ContainerIncrementingTable;
import org.labkey.laboratory.query.LaboratoryWorkbooksTable;
import org.labkey.api.ldk.table.ContainerScopedTable;

/**
 * User: bimber
 * Date: 1/20/13
 * Time: 7:57 AM
 */
public class LaboratoryUserSchema extends SimpleUserSchema
{
    private LaboratoryUserSchema(User user, Container container, DbSchema schema)
    {
        super(LaboratoryModule.SCHEMA_NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = DbSchema.get(LaboratoryModule.SCHEMA_NAME);

        DefaultSchema.registerProvider(LaboratoryModule.SCHEMA_NAME, new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(final DefaultSchema schema)
            {
                if (schema.getContainer().getActiveModules().contains(m))
                {
                    return new LaboratoryUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
                }
                return null;
            }
        });
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable)
    {
        if (LaboratorySchema.TABLE_SUBJECTS.equalsIgnoreCase(name))
            return getSubjectsTable(name, sourceTable);
        else if (LaboratorySchema.TABLE_FREEZERS.equalsIgnoreCase(name))
            return getContainerScopedTable(name, sourceTable, "name");
        else if (LaboratorySchema.TABLE_SAMPLE_TYPE.equalsIgnoreCase(name))
            return getContainerScopedTable(name, sourceTable, "type");
        else if (LaboratorySchema.TABLE_DNA_OLIGOS.equalsIgnoreCase(name))
            return getDnaOligosTable(name, sourceTable);
        else if (LaboratorySchema.TABLE_PEPTIDES.equalsIgnoreCase(name))
            return getPeptideTable(name, sourceTable);
        else if (LaboratorySchema.TABLE_ANTIBODIES.equalsIgnoreCase(name))
            return getAntibodiesTable(name, sourceTable);
        else if (LaboratorySchema.TABLE_WORKBOOKS.equalsIgnoreCase(name))
            return getWorkbooksTable(name, sourceTable);
        else if (LaboratorySchema.TABLE_SAMPLES.equalsIgnoreCase(name))
            return getSamplesTable(name, sourceTable);
        else
            return super.createWrappedTable(name, sourceTable);
    }

    private SimpleTable getSubjectsTable(String name, @NotNull TableInfo schematable)
    {
        return new ContainerScopedTable(this, schematable, "subjectname").init();
    }

    private TableInfo getContainerScopedTable(String name, @NotNull TableInfo schematable, String pkCol)
    {
        return new ContainerScopedTable(this, schematable, pkCol).init();
    }

    private SimpleTable getDnaOligosTable(String name, @NotNull TableInfo schematable)
    {
        return new ContainerIncrementingTable(this, schematable, "oligo_id").init();
    }

    private SimpleTable getSamplesTable(String name, @NotNull TableInfo schematable)
    {
        return new ContainerIncrementingTable(this, schematable, "freezerid").init();
    }

    private SimpleTable getPeptideTable(String name, @NotNull TableInfo schematable)
    {
        return new ContainerIncrementingTable(this, schematable, "peptideId").init();
    }

    private SimpleTable getAntibodiesTable(String name, @NotNull TableInfo schematable)
    {
        return new ContainerIncrementingTable(this, schematable, "antibodyId").init();
    }

    private SimpleTable getWorkbooksTable(String name, @NotNull TableInfo schematable)
    {
        return new LaboratoryWorkbooksTable(this, schematable).init();
    }
}
