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

package org.labkey.onprc_billing;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.dialect.SqlDialect;

public class ONPRC_BillingSchema
{
    public static final String NAME = "onprc_billing";

    private static final ONPRC_BillingSchema _instance = new ONPRC_BillingSchema();

    public static final String TABLE_INVOICED_ITEMS = "invoicedItems";
    public static final String TABLE_INVOICE_RUNS = "invoiceRuns";
    public static final String TABLE_MISC_CHARGES = "miscCharges";
    public static final String TABLE_PROJECT_ACCOUNT_HISTORY = "projectAccountHistory";
    public static final String TABLE_CHARGE_RATES = "chargeRates";
    public static final String TABLE_CHARGE_RATE_EXEMPTIONS = "chargeRateExemptions";
    public static final String TABLE_CHARGE_UNIT_ACCOUNT = "chargeUnitAccounts";
    public static final String TABLE_CREDIT_ACCOUNT = "creditAccount";
    public static final String TABLE_CREDIT_GRANTS = "grants";
    public static final String TABLE_ALIASES = "aliases";
    public static final String TABLE_CHARGEABLE_ITEMS = "chargeableItems";

    public static ONPRC_BillingSchema getInstance()
    {
        return _instance;
    }

    private ONPRC_BillingSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.onprc_billing.ONPRC_BillingSchema.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get("onprc_billing");
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}
