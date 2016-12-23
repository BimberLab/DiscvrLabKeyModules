package org.labkey.extscheduler.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.extscheduler.ExtSchedulerManager;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

public class EventsTable extends FilteredTable<ExtSchedulerQuerySchema>
{
    public EventsTable(TableInfo table, ExtSchedulerQuerySchema schema)
    {
        super(table, schema);
        wrapAllColumns(true);

        SqlDialect dialect = getSchema().getSqlDialect();
        SQLFragment isOwnerSQL = new SQLFragment("CASE WHEN "+ ExprColumn.STR_TABLE_ALIAS +".UserId = ? OR " + ExprColumn.STR_TABLE_ALIAS +".CreatedBy = ? THEN "
                + dialect.getBooleanTRUE() + " ELSE " + dialect.getBooleanFALSE() + " END");
        isOwnerSQL.add(schema.getUser().getUserId());
        isOwnerSQL.add(schema.getUser().getUserId());
        ExprColumn isOwnerCol = new ExprColumn(this, "Owner", isOwnerSQL, JdbcType.BOOLEAN);
        isOwnerCol.setReadOnly(true);
        isOwnerCol.setHidden(true);
        addColumn(isOwnerCol);

        // disable bulk import for all users
        setImportURL(AbstractTableInfo.LINK_DISABLER);

        // only allow admins to see insert new button and edit links
        if (!getContainer().hasPermission(schema.getUser(), AdminPermission.class))
        {
            setInsertURL(AbstractTableInfo.LINK_DISABLER);
            setUpdateURL(AbstractTableInfo.LINK_DISABLER);
        }
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, getRealTable())
        {
            @Override
            protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
            {
                boolean isAdmin = container.hasPermission(user, AdminPermission.class);

                // only allow non-admin users to insert records in the future
                if (!isAdmin)
                {
                    validateEventNotInThePast(row, "created");
                }

                return super.insertRow(user, container, row);
            }

            @Override
            protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
            {
                try
                {
                    return super._insert(user, c, row);
                }
                catch (Exception e)
                {
                    if (e.getMessage().contains("Start/End date ranges may not overlap for the same resource."))
                        throw new ValidationException("Resource is not available, please select another time slot.");
                    else
                        throw e;
                }
            }

            @Override
            protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
            {
                boolean isAdmin = container.hasPermission(user, AdminPermission.class);

                // only allow non-admin users to update events if they created it or are
                // assigned to the event and the event has not already occurred
                //if (!isAdmin)
                //{
                //    validateUserCanUpdateEvent(user, oldRow, "update");
                //    validateEventNotInThePast(oldRow, "updated");
                //    validateEventNotInThePast(row, "updated");
                //}

                // only allow admin users to update events
                if (!isAdmin)
                    throw new UnauthorizedException("User does not have permissions ot perform this operation. Must be an admin to update an event.");

                return super.updateRow(user, container, row, oldRow);
            }

            @Override
            protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
            {
                try
                {
                    return super._update(user, c, row, oldRow, keys);
                }
                catch (Exception e)
                {
                    if (e.getMessage().contains("Start/End date ranges may not overlap for the same resource."))
                        throw new ValidationException("Resource is not available, please select another time slot.");
                    else
                        throw e;
                }
            }

            @Override
            protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
            {
                boolean isAdmin = container.hasPermission(user, AdminPermission.class);

                // only allow non-admin users to delete events if they created it or are
                // assigned to the event and the event has not already occurred
                if (!isAdmin)
                {
                    validateUserCanUpdateEvent(user, oldRowMap, "delete");
                    validateEventNotInThePast(oldRowMap, "deleted");
                }

                return super.deleteRow(user, container, oldRowMap);
            }

            private void validateUserCanUpdateEvent(User user, Map<String, Object> row, String operation)
            {
                boolean isOwner = ExtSchedulerManager.getInstance().isEventOwner(user, row);
                boolean isCreator = ExtSchedulerManager.getInstance().isEventCreator(user, row);
                if (!isOwner && !isCreator)
                {
                    throw new UnauthorizedException("User does not have permissions ot perform this operation. "
                            + "Must be the owner or creator in order to " + operation + " an event.");
                }
            }

            private void validateEventNotInThePast(Map<String, Object> row, String operation)
            {
                boolean isInPast = ExtSchedulerManager.getInstance().isEventInPast(row);
                if (isInPast)
                {
                    throw new UnauthorizedException("User does not have permissions to perform this operation. "
                            + "Only events in the future can be " + operation + ".");
                }
            }
        };

    }
}