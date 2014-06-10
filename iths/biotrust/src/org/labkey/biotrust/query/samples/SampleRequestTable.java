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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.biotrust.BioTrustContactsManager;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.BioTrustTable;
import org.labkey.biotrust.query.DefaultBioTrustQueryUpdateService;
import org.labkey.biotrust.security.SamplePickupRole;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 2/15/13
 */
public class SampleRequestTable extends BioTrustTable
{
    public SampleRequestTable(final BioTrustQuerySchema schema)
    {
        super(BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME, schema, SampleRequestDomain.NAME);
        setAuditBehavior(AuditBehaviorType.DETAILED);

        ColumnInfo studyCol = getColumn(FieldKey.fromParts("studyId"));
        UserSchema userSchema = QueryService.get().getUserSchema(schema.getUser(), schema.getContainer(), BioTrustQuerySchema.NAME);
        ForeignKey fk = new QueryForeignKey(userSchema, null, BioTrustQuerySchema.STUDY_REGISTRATION_TABLE_NAME, "RowId", "StudyDescription");

        studyCol.setFk(fk);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new SampleRequestUpdateService(this, table);
        return null;
    }

    private static class SampleRequestUpdateService extends DefaultBioTrustQueryUpdateService
    {
        public SampleRequestUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container c, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Set<String> rowUpdateKeys = new HashSet<>();
            for (String key : row.keySet())
            {
                if ("RowId".equals(key) || oldRow.get(key) == null || !oldRow.get(key).equals(row.get(key)))
                    rowUpdateKeys.add(key);
            }

            checkSampleRequest(c, user, (Integer)oldRow.get("rowId"), rowUpdateKeys);

            // check for updates to contact information
            Object pickupUserInSystem = row.get(SampleRequestDomain.SAMPLE_PICKUP_CONTACT_INSYSTEM_PROPERTY_NAME);
            Object pickupUserId = row.get(SampleRequestDomain.SAMPLE_PICKUP_CONTACT_PROPERTY_NAME);
            if (BooleanUtils.toBoolean(String.valueOf(pickupUserInSystem)) && pickupUserId != null)
                updateContactRole(c, NumberUtils.toInt(String.valueOf(pickupUserId), -1), RoleManager.getRole(SamplePickupRole.class));

            Object secondaryUserInSystem = row.get(SampleRequestDomain.SECONDARY_PICKUP_CONTACT_INSYSTEM_PROPERTY_NAME);
            Object secondaryUserId = row.get(SampleRequestDomain.SECONDARY_PICKUP_CONTACT_PROPERTY_NAME);
            if (BooleanUtils.toBoolean(String.valueOf(secondaryUserInSystem)) && secondaryUserId != null)
                updateContactRole(c, NumberUtils.toInt(String.valueOf(secondaryUserId), -1), RoleManager.getRole(SamplePickupRole.class));

            return super.updateRow(user, c, row, oldRow);
        }
    }

    private static void updateContactRole(Container c, int userId, Role role)
    {
        if (userId != -1)
        {
            User user = UserManager.getUser(userId);
            if (user != null)
                BioTrustContactsManager.get().addRoleAssignment(c, user, role);
        }
    }
}
