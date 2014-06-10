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
package org.labkey.biotrust.query.study;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.BioTrustTable;
import org.labkey.biotrust.query.DefaultBioTrustQueryUpdateService;

import java.sql.SQLException;
import java.util.Map;

/**
 * User: cnathe
 * Date: 2/18/13
 */
public class StudyRegistrationTable extends BioTrustTable
{
    public StudyRegistrationTable(BioTrustQuerySchema schema)
    {
        super(BioTrustQuerySchema.STUDY_REGISTRATION_TABLE_NAME, schema, StudyRegistrationDomain.NAME);

        setAuditBehavior(AuditBehaviorType.DETAILED);

        /*
            ColumnInfo piCol = getColumn(FieldKey.fromParts(StudyRegistrationDomain.PRINCIPAL_INVESTIGATOR_PROPERTY_NAME));
            if (piCol != null)
                UserIdForeignKey.initColumn(piCol);

            ColumnInfo primaryCol = getColumn(FieldKey.fromParts(StudyRegistrationDomain.PRIMARY_STUDY_CONTACT_PROPERTY_NAME));
            if (primaryCol != null)
                UserIdForeignKey.initColumn(primaryCol);
        */
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new StudyRegistrationUpdateService(this, table);
        return null;
    }

    private static class StudyRegistrationUpdateService extends DefaultBioTrustQueryUpdateService
    {
        public StudyRegistrationUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }
    }
}

