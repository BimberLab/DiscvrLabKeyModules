package org.labkey.blast.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.SharedDataTable;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.blast.BLASTSchema;

/**
 * User: bimber
 * Date: 7/22/2014
 * Time: 3:05 PM
 */
public class BlastUserSchema extends SimpleUserSchema
{
    private BlastUserSchema(User user, Container container, DbSchema schema)
    {
        super(BLASTSchema.NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = BLASTSchema.getInstance().getSchema();

        DefaultSchema.registerProvider(BLASTSchema.NAME, new DefaultSchema.SchemaProvider(m)
        {
            @Override
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new BlastUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable, ContainerFilter cf)
    {
        if (BLASTSchema.TABLE_DATABASES.equalsIgnoreCase(name))
            return new SharedDataTable<>(this, sourceTable).init();
        else
            return super.createWrappedTable(name, sourceTable, cf);
    }
}
