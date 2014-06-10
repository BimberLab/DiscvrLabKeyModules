/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.biotrust.query.samples;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.biotrust.BioTrustSampleManager;
import org.labkey.biotrust.BioTrustSchema;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.biotrust.BioTrustContactsManager;
import org.labkey.biotrust.model.TissueRecord;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.DefaultBioTrustQueryUpdateService;
import org.labkey.biotrust.security.SamplePickupRole;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

/**
 * User: cnathe
 * Date: 2/23/13
 */
public class SamplePickupTable extends FilteredTable<BioTrustQuerySchema>
{
    public SamplePickupTable(TableInfo ti, BioTrustQuerySchema schema)
    {
        super(ti, schema);
        wrapAllColumns(true);

        ColumnInfo createdBy = getColumn(FieldKey.fromParts("CreatedBy"));
        UserIdForeignKey.initColumn(createdBy);

        ColumnInfo modifiedBy = getColumn(FieldKey.fromParts("ModifiedBy"));
        UserIdForeignKey.initColumn(modifiedBy);

        ColumnInfo pickupContactCol = getColumn(FieldKey.fromParts("PickupContact"));
        UserIdForeignKey.initColumn(pickupContactCol);

        ColumnInfo container = getColumn("Container");
        ContainerForeignKey.initColumn(container, getUserSchema());

        // add joined column for tissue record ID
        SQLFragment tissueIdSQL = new SQLFragment("(SELECT MAX(sMap.TissueId) FROM ");
        tissueIdSQL.append(BioTrustSchema.getInstance().getTableInfoSamplePickupMap(), "sMap");
        tissueIdSQL.append(" WHERE sMap.PickupId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId ");
        tissueIdSQL.append(" AND sMap.Container = " + ExprColumn.STR_TABLE_ALIAS + ".Container)");
        ColumnInfo tissueIdCol = new ExprColumn(this, "TissueId", tissueIdSQL, JdbcType.INTEGER);
        addColumn(tissueIdCol);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new SamplePickupTableUpdateService(this, table);
        return null;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    private static class SamplePickupTableUpdateService extends DefaultBioTrustQueryUpdateService
    {
        public SamplePickupTableUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            checkTissueRecordForSamplePickup(container, user, (Integer)oldRow.get("rowId"));
            return super.updateRow(user, container, row, oldRow);
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            Map<String, Object> newRow = super._update(user, c, row, oldRow, keys);
            updateRoleAssigment(c, user, newRow);
            return newRow;
        }

        @Override
        protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
        {
            Map<String, Object> newRow =  super._insert(user, c, row);
            updateRoleAssigment(c, user, newRow);
            return newRow;
        }

        private void checkTissueRecordForSamplePickup(Container c, User user, int rowId) throws QueryUpdateServiceException
        {
            // check the tissue records study registration record for each mapped pickup record
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("PickupId"), rowId);
            filter.addCondition(FieldKey.fromParts("Container"), c.getId());
            TableSelector selector = new TableSelector(BioTrustSchema.getInstance().getTableInfoSamplePickupMap(),
                    Collections.singleton("TissueId"), filter, null);
            for (Integer tissueId : selector.getArray(Integer.class))
            {
                TissueRecord tissueRecord = BioTrustSampleManager.get().getTissueRecord(c, user, tissueId);
                if (tissueRecord != null)
                {
                    checkSampleRequest(c, user, tissueRecord.getSampleId());
                }
            }
        }

        private void updateRoleAssigment(Container c, User user, Map<String, Object> row)
        {
            if (row.containsKey("pickupContact"))
            {
                Integer userId = (Integer)row.get("pickupContact");
                Role role = RoleManager.getRole(SamplePickupRole.class);
                BioTrustContactsManager.get().addRoleAssignment(c, UserManager.getUser(userId), role);
            }
        }
    }
}
