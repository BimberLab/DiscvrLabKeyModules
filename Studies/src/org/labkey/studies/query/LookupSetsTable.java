package org.labkey.studies.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.ContainerScopedTable;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.Map;

public class LookupSetsTable<SchemaType extends UserSchema> extends ContainerScopedTable<SchemaType>
{
    public LookupSetsTable(SchemaType schema, TableInfo st, ContainerFilter cf, String newPk)
    {
        super(schema, st, cf, newPk);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }

    private class UpdateService extends ContainerScopedTable<SchemaType>.UpdateService
    {
        public UpdateService(SimpleUserSchema.SimpleTable<SchemaType> ti)
        {
            super(ti);
        }

        @Override
        protected void afterInsertUpdate(int count, BatchValidationException errors)
        {
            StudiesUserSchema.repopulateCaches(getUserSchema().getUser(), getUserSchema().getContainer());
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            Map<String, Object> row = super.deleteRow(user, container, oldRowMap);
            StudiesUserSchema.repopulateCaches(getUserSchema().getUser(), getUserSchema().getContainer());
            return row;
        }

        @Override
        protected int truncateRows(User user, Container container) throws QueryUpdateServiceException, SQLException
        {
            int i = super.truncateRows(user, container);
            StudiesUserSchema.repopulateCaches(getUserSchema().getUser(), getUserSchema().getContainer());
            return i;
        }
    }
}
