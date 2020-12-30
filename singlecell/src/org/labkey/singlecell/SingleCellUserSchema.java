package org.labkey.singlecell;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.ContainerScopedTable;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;

/**
 * Created by bimber on 6/18/2016.
 */
public class SingleCellUserSchema extends SimpleUserSchema
{
    private SingleCellUserSchema(User user, Container container, DbSchema schema)
    {
        super(SingleCellSchema.NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = SingleCellSchema.getInstance().getSchema();

        DefaultSchema.registerProvider(SingleCellSchema.NAME, new DefaultSchema.SchemaProvider(m)
        {
            @Override
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new SingleCellUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable, ContainerFilter cf)
    {
        if (SingleCellSchema.TABLE_CITE_SEQ_ANTIBODIES.equalsIgnoreCase(name))
        {
            return new ContainerScopedTable<>(this, sourceTable, cf, "antibodyName").init();
        }
        else if (SingleCellSchema.TABLE_STIM_TYPES.equalsIgnoreCase(name))
        {
            return new ContainerScopedTable<>(this, sourceTable, cf, "name").init();
        }
        else if (SingleCellSchema.TABLE_ASSAY_TYPES.equalsIgnoreCase(name))
        {
            return new ContainerScopedTable<>(this, sourceTable, cf, "name").init();
        }

        return super.createWrappedTable(name, sourceTable, cf);
    }
}