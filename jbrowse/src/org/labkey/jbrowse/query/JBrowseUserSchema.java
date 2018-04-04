package org.labkey.jbrowse.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.SharedDataTable;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.jbrowse.JBrowseSchema;

/**
 * User: bimber
 * Date: 7/22/2014
 * Time: 3:03 PM
 */
public class JBrowseUserSchema extends SimpleUserSchema
{
    private JBrowseUserSchema(User user, Container container, DbSchema schema)
    {
        super(JBrowseSchema.NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = JBrowseSchema.getInstance().getSchema();

        DefaultSchema.registerProvider(JBrowseSchema.NAME, new DefaultSchema.SchemaProvider(m)
        {
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new JBrowseUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable)
    {
        if (JBrowseSchema.TABLE_JSONFILES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else
            return super.createWrappedTable(name, sourceTable);
    }
}
