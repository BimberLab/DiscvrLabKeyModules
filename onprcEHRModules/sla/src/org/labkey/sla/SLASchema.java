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

package org.labkey.sla;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;

public class SLASchema
{
    public static final String NAME = "sla";
    public static final String TABLE_ETL_RUNS = "etl_runs";

    private static final SLASchema _instance = new SLASchema();

    public static SLASchema getInstance()
    {
        return _instance;
    }

    private SLASchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.sla.SLASchema.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get("sla");
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoPurchase()
    {
        return getSchema().getTable("purchase");
    }

    public TableInfo getTableInfoPurchaseDetails()
    {
        return getSchema().getTable("purchasedetails");
    }

    public TableInfo getTableInfoPurchaseDrafts()
    {
        return getSchema().getTable("purchasedrafts");
    }

    public TableInfo getTableInfoRequestors()
    {
        return getSchema().getTable("requestors");
    }

    public TableInfo getTableInfoVendors()
    {
        return getSchema().getTable("vendors");
    }
}
