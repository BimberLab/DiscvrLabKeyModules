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
package org.labkey.biotrust.query.contacts;

import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.BioTrustTable;
import org.labkey.biotrust.query.DefaultBioTrustQueryUpdateService;

/**
 * User: cnathe
 * Date: 7/17/13
 */
public class ContactTable extends BioTrustTable
{
    public ContactTable(BioTrustQuerySchema schema)
    {
        super(BioTrustQuerySchema.CONTACT_TABLE_NAME, schema, ContactDomain.NAME);
    }

    @Override
    public String getDescription()
    {
        return "Information about out-of-system contacts for the NWBT";
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new ContactTableUpdateService(this, table);
        return null;
    }

    private static class ContactTableUpdateService extends DefaultBioTrustQueryUpdateService
    {
        public ContactTableUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }
    }
}
