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

package org.labkey.biotrust;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.biotrust.query.BioTrustQuerySchema;

public class BioTrustSchema
{
    private static final BioTrustSchema _instance = new BioTrustSchema();

    public static BioTrustSchema getInstance()
    {
        return _instance;
    }

    private BioTrustSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.biotrust.BioTrustSchema.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(BioTrustQuerySchema.NAME);
    }

    public TableInfo getTableInfoRequestCategory()
    {
        return getSchema().getTable(BioTrustQuerySchema.REQUEST_CATEGORY_TABLE_NAME);
    }

    public TableInfo getTableInfoRequestStatus()
    {
        return getSchema().getTable(BioTrustQuerySchema.REQUEST_STATUS_TABLE_NAME);
    }

    public TableInfo getTableInfoDocumentTypes()
    {
        return getSchema().getTable(BioTrustQuerySchema.DOCUMENT_TYPES_TABLE_NAME);
    }

    public TableInfo getTableInfoRequiredDocuments()
    {
        return getSchema().getTable(BioTrustQuerySchema.REQUIRED_DOCUMENTS_TABLE_NAME);
    }

    public TableInfo getTableInfoSpecimenRequestDocuments()
    {
        return getSchema().getTable(BioTrustQuerySchema.SPECIMEN_REQUEST_DOCUMENTS_TABLE_NAME);
    }

    public TableInfo getTableInfoDocumentProperties()
    {
        return getSchema().getTable(BioTrustQuerySchema.DOCUMENT_PROPERTIES_TABLE_NAME);
    }

    public TableInfo getTableInfoSamplePickup()
    {
        return getSchema().getTable(BioTrustQuerySchema.SAMPLE_PICKUP_TABLE_NAME);
    }

    public TableInfo getTableInfoSamplePickupMap()
    {
        return getSchema().getTable(BioTrustQuerySchema.SAMPLE_PICKUP_MAP_TABLE_NAME);
    }

    public TableInfo getTableInfoParticipantEligibilityMap()
    {
        return getSchema().getTable(BioTrustQuerySchema.PARTICIPANT_ELIGIBILITY_MAP_TABLE_NAME);
    }

    public TableInfo getTableInfoSampleReviewerMap()
    {
        return getSchema().getTable(BioTrustQuerySchema.SAMPLE_REVIEWER_MAP_TABLE_NAME);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}
