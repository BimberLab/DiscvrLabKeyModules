/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.biotrust.BioTrustSampleManager;
import org.labkey.biotrust.BioTrustSchema;
import org.labkey.biotrust.model.TissueRecord;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.BioTrustTable;
import org.labkey.biotrust.query.DefaultBioTrustQueryUpdateService;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

/**
 * User: klum
 * Date: 2/16/13
 */
public class ParticipantEligibilityTable extends BioTrustTable
{
    public ParticipantEligibilityTable(BioTrustQuerySchema schema)
    {
        super(BioTrustQuerySchema.PARTICIPANT_ELIGIBILITY_TABLE_NAME, schema, ParticipantEligibilityDomain.NAME);

        // add joined column for tissue record ID
        SQLFragment tissueIdSQL = new SQLFragment("(SELECT MAX(peMap.TissueId) FROM ");
        tissueIdSQL.append(BioTrustSchema.getInstance().getTableInfoParticipantEligibilityMap(), "peMap");
        tissueIdSQL.append(" WHERE peMap.EligibilityId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId ");
        tissueIdSQL.append(" AND peMap.Container = " + ExprColumn.STR_TABLE_ALIAS + ".Container)");
        ColumnInfo tissueIdCol = new ExprColumn(this, "TissueId", tissueIdSQL, JdbcType.INTEGER);
        addColumn(tissueIdCol);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new ParticipantEligibilityTableUpdateService(this, table);
        return null;
    }

    private static class ParticipantEligibilityTableUpdateService extends DefaultBioTrustQueryUpdateService
    {
        public ParticipantEligibilityTableUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            checkTissueRecordForParticipantEligibility(container, user, (Integer) oldRow.get("rowId"));
            return super.updateRow(user, container, row, oldRow);
        }

        private void checkTissueRecordForParticipantEligibility(Container c, User user, int rowId) throws QueryUpdateServiceException
        {
            // check the tissue records study registration record for each mapped ptid eligibility record
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("EligibilityId"), rowId);
            filter.addCondition(FieldKey.fromParts("Container"), c.getId());
            TableSelector selector = new TableSelector(BioTrustSchema.getInstance().getTableInfoParticipantEligibilityMap(),
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
    }
}
