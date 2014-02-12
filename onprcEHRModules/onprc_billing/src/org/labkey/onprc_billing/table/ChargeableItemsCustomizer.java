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
package org.labkey.onprc_billing.table;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.DetailsURL;
import org.labkey.onprc_billing.ONPRC_BillingSchema;

/**
 * User: bimber
 * Date: 10/11/13
 * Time: 3:51 PM
 */
public class ChargeableItemsCustomizer implements TableCustomizer
{
    public ChargeableItemsCustomizer()
    {

    }

    public void customize(TableInfo table)
    {
        if (table instanceof AbstractTableInfo)
        {
            if (table.getName().equalsIgnoreCase("chargeableItems"))
            {
                boolean isPublic = !table.getUserSchema().getName().equalsIgnoreCase(ONPRC_BillingSchema.NAME);
                ((AbstractTableInfo) table).setDetailsURL(DetailsURL.fromString("/onprc_billing/chargeItemDetails.view?chargeId=${rowid}" + (isPublic ? "&isPublic=true" : "")));
            }
        }

    }
}
