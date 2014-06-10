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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.BioTrustTable;
import org.labkey.biotrust.query.DefaultBioTrustQueryUpdateService;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 2/16/13
 */
public class TissueRecordTable extends BioTrustTable
{
    public TissueRecordTable(final BioTrustQuerySchema schema)
    {
        super(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME, schema, TissueRecordDomain.NAME);

        ColumnInfo sampleCol = getColumn(FieldKey.fromParts("sampleId"));
        UserSchema userSchema = QueryService.get().getUserSchema(schema.getUser(), schema.getContainer(), BioTrustQuerySchema.NAME);
        ForeignKey fk = new QueryForeignKey(userSchema, null, BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME, "RowId", "RequestType");

        sampleCol.setFk(fk);

        // join in the study Id
        SQLFragment studyIdSQL = new SQLFragment("(SELECT StudyId FROM ");
        studyIdSQL.append(schema.getTable(BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME), "sr");
        studyIdSQL.append(" WHERE sr.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".SampleId ");
        studyIdSQL.append(" AND sr.Container = " + ExprColumn.STR_TABLE_ALIAS + ".Container)");
        ColumnInfo studyIdCol = new ExprColumn(this, "StudyId", studyIdSQL, JdbcType.INTEGER);
        addColumn(studyIdCol);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new TissueRecordUpdateService(this, table);
        return null;
    }

    private static class TissueRecordUpdateService extends DefaultBioTrustQueryUpdateService
    {
        public TissueRecordUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container c, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            checkSampleRequest(c, user, (Integer)row.get("sampleId"));
            return super.insertRow(user, c, row);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Set<String> rowUpdateKeys = new HashSet<>();
            for (String key : row.keySet())
            {
                if (oldRow.get(key) == null || !oldRow.get(key).equals(row.get(key)))
                    rowUpdateKeys.add(key);
            }

            checkSampleRequest(c, user, (Integer)oldRow.get("sampleId"), rowUpdateKeys);
            return super.updateRow(user, c, row, oldRow);
        }
    }
}
