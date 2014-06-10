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
package org.labkey.biotrust.security;

import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.SeeUserEmailAddressesPermission;
import org.labkey.api.security.permissions.UpdatePermission;

/**
 * User: klum
 * Date: 1/22/13
 */
public class PrincipalInvestigatorRole extends AbstractBioTrustRole
{
    public PrincipalInvestigatorRole()
    {
        super("NWBT Principal Investigator",
                "NWBioTrust principal investigators can create and submit specimen request forms as well as approve " +
                        "request form edits.",
                ReadPermission.class,
                ReadSomePermission.class,
                InsertPermission.class,
                UpdatePermission.class,
                DeletePermission.class,
                SubmitRequestsPermission.class,
                ApproveRequestsPermission.class,
                CreateContactsPermission.class,
                SeeUserEmailAddressesPermission.class
        );
    }

    @Override
    public boolean isContactRole()
    {
        return true;
    }
}
