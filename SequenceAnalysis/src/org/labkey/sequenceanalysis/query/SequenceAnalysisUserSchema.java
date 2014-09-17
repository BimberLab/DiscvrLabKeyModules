package org.labkey.sequenceanalysis.query;

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
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

/**
 * User: bimber
 * Date: 7/22/2014
 * Time: 1:49 PM
 */
public class SequenceAnalysisUserSchema extends SimpleUserSchema
{
    private SequenceAnalysisUserSchema(User user, Container container, DbSchema schema)
    {
        super(SequenceAnalysisSchema.SCHEMA_NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = DbSchema.get(SequenceAnalysisSchema.SCHEMA_NAME);

        DefaultSchema.registerProvider(SequenceAnalysisSchema.SCHEMA_NAME, new DefaultSchema.SchemaProvider(m)
        {
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new SequenceAnalysisUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable)
    {
        if (SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_REF_LIBRARIES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_LIBRARY_TRACKS.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_AA_FEATURES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_NT_FEATURES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_SAVED_ANALYSES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else
            return super.createWrappedTable(name, sourceTable);
    }
}
