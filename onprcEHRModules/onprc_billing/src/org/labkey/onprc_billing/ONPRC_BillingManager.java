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

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.Queryable;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 9/21/13
 * Time: 9:55 AM
 */
public class ONPRC_BillingManager
{
    private static ONPRC_BillingManager _instance = new ONPRC_BillingManager();
    public static final String BillingContainerPropName = "BillingContainer";
    public static final String IssuesContainerPropName = "IssuesContainer";
    public static final String SLAContainerPropName = "SLAContainer";

    @Queryable
        public static final String DAY_LEASE_MAX_DURATION = "14";
    @Queryable
    public static final String DAY_LEASE_NAME = "One Day Lease";
    @Queryable
    public static final String TMB_LEASE_NAME = "Animal Lease Fee - TMB";
    @Queryable
    public static final String LEASE_FEE_ADJUSTMENT = "Lease Fee Adjustment";
    @Queryable
    public static final String LEASE_SETUP_FEES = "Lease Setup Fees";

    private ONPRC_BillingManager()
    {

    }

    public static ONPRC_BillingManager get()
    {
        return _instance;
    }

    public List<String> deleteBillingRuns(User user, Collection<String> pks, boolean testOnly)
    {
        TableInfo invoiceRuns = ONPRC_BillingSchema.getInstance().getSchema().getTable(ONPRC_BillingSchema.TABLE_INVOICE_RUNS);
        TableInfo invoicedItems = ONPRC_BillingSchema.getInstance().getSchema().getTable(ONPRC_BillingSchema.TABLE_INVOICED_ITEMS);
        TableInfo miscCharges = ONPRC_BillingSchema.getInstance().getSchema().getTable(ONPRC_BillingSchema.TABLE_MISC_CHARGES);

        //create filters
        SimpleFilter invoiceRunFilter = new SimpleFilter(FieldKey.fromString("invoiceId"), pks, CompareType.IN);

        SimpleFilter miscChargesFilter = new SimpleFilter(FieldKey.fromString("invoiceId"), pks, CompareType.IN);

        //perform the work
        List<String> ret = new ArrayList<>();
        if (testOnly)
        {
            TableSelector tsRuns = new TableSelector(invoicedItems, invoiceRunFilter, null);
            ret.add(tsRuns.getRowCount() + " records from invoiced items");

            TableSelector tsMiscCharges2 = new TableSelector(miscCharges, miscChargesFilter, null);
            ret.add(tsMiscCharges2.getRowCount() + " records from misc charges will be removed from the deleted invoice, which means they will be picked up by the next billing period.  They are not deleted.");
        }
        else
        {
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                long deleted1 = Table.delete(invoicedItems, invoiceRunFilter);

                TableSelector tsMiscCharges2 = new TableSelector(miscCharges, Collections.singleton("objectid"), miscChargesFilter, null);
                String[] miscChargesIds = tsMiscCharges2.getArray(String.class);
                for (String objectid : miscChargesIds)
                {
                    Map<String, Object> map = new CaseInsensitiveHashMap<>();
                    map.put("invoiceId", null);
                    map = Table.update(user, miscCharges, map, objectid);
                }

                long deleted3 = Table.delete(invoiceRuns, new SimpleFilter(FieldKey.fromString("objectid"), pks, CompareType.IN));

                transaction.commit();
            }
        }

        return ret;
    }

    public Container getBillingContainer(Container c)
    {
        Module billing = ModuleLoader.getInstance().getModule(ONPRC_BillingModule.NAME);
        ModuleProperty mp = billing.getModuleProperties().get(BillingContainerPropName);
        String path = mp.getEffectiveValue(c);
        if (path == null)
            return null;

        return ContainerManager.getForPath(path);

    }

    public Container getSLADataFolder(Container c)
    {
        Module m = ModuleLoader.getInstance().getModule("sla");
        ModuleProperty mp = m.getModuleProperties().get(SLAContainerPropName);
        String path = mp.getEffectiveValue(c);
        if (path == null)
            return null;

        return ContainerManager.getForPath(path);

    }
}