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

package org.labkey.biotrust;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;

public class BioTrustContainerListener extends ContainerManager.AbstractContainerListener
{
    @Override
    public void containerDeleted(Container c, User user)
    {
        DbSchema schema = BioTrustSchema.getInstance().getSchema();
        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
        {
            ContainerUtil.purgeTable(BioTrustSchema.getInstance().getTableInfoSamplePickupMap(), c, null);
            ContainerUtil.purgeTable(BioTrustSchema.getInstance().getTableInfoParticipantEligibilityMap(), c, null);
            ContainerUtil.purgeTable(BioTrustSchema.getInstance().getTableInfoSamplePickup(), c, null);
            ContainerUtil.purgeTable(BioTrustSchema.getInstance().getTableInfoSampleReviewerMap(), c, null);

            transaction.commit();
        }
    }
}